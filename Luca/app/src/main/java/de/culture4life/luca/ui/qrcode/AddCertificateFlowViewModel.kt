package de.culture4life.luca.ui.qrcode

import android.app.Application
import android.webkit.URLUtil
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.document.*
import de.culture4life.luca.meeting.MeetingManager
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class AddCertificateFlowViewModel(app: Application) : BaseFlowViewModel(app) {

    private val consentManager: ConsentManager = application.consentManager
    private var importError: ViewError? = null
    private var hasAddedDocument = false

    val addedDocument = MutableLiveData<ViewEvent<Document>>()
    val documentAddedOnViewDismissed = MutableLiveData<ViewEvent<Boolean>>()

    fun process(barcodeData: String): Completable {
        return parseAndValidateDocument(barcodeData)
            .flatMapCompletable {
                requestImportConsent()
                    .observeOn(Schedulers.io())
                    .andThen(addDocument(it))
            }
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
    }

    private fun parseAndValidateDocument(encodedDocument: String): Single<Document> {
        return application.documentManager.parseAndValidateEncodedDocument(encodedDocument)
            .doOnSubscribe { Timber.d("Attempting to parse encoded document: $encodedDocument") }
            .doOnSuccess {
                Timber.d("Parsed document: $it")
                removeError(importError)
            }
            .doOnError { throwable ->
                Timber.w("Unable to parse document: $throwable")
                val errorBuilder: ViewError.Builder = createErrorBuilder(throwable)
                    .withTitle(R.string.document_import_error_title)
                if (throwable is DocumentParsingException) {
                    if (MeetingManager.isPrivateMeeting(encodedDocument) || CheckInManager.isSelfCheckInUrl(encodedDocument)) {
                        // the user tried to check-in from the wrong tab
                        errorBuilder.withTitle(R.string.document_import_error_check_in_scanner_title)
                        errorBuilder.withDescription(R.string.document_import_error_check_in_scanner_description)
                    } else if (URLUtil.isValidUrl(encodedDocument) && !DocumentManager.isTestResult(encodedDocument)) {
                        // data is actually an URL that the user may want to open
                        errorBuilder.withDescription(R.string.document_import_error_unsupported_but_url_description)
                        errorBuilder.withResolveLabel(R.string.action_continue)
                        errorBuilder.withResolveAction(Completable.fromAction { application.openUrl(encodedDocument) })
                    } else {
                        errorBuilder.withDescription(R.string.document_import_error_unsupported_description)
                    }
                } else if (throwable is DocumentExpiredException) {
                    errorBuilder.withDescription(R.string.document_import_error_expired_description)
                } else if (throwable is DocumentVerificationException) {
                    when (throwable.reason) {
                        DocumentVerificationException.Reason.NAME_MISMATCH -> if (application.childrenManager.hasChildren().blockingGet()) {
                            errorBuilder.withDescription(R.string.document_import_error_name_mismatch_including_children_description)
                        } else {
                            errorBuilder.withDescription(R.string.document_import_error_name_mismatch_description)
                        }
                        DocumentVerificationException.Reason.INVALID_SIGNATURE -> errorBuilder.withDescription(R.string.document_import_error_invalid_signature_description)
                        DocumentVerificationException.Reason.DATE_OF_BIRTH_TOO_OLD_FOR_CHILD -> errorBuilder.withDescription(R.string.document_import_error_child_too_old_description)
                        DocumentVerificationException.Reason.TIMESTAMP_IN_FUTURE -> errorBuilder.withDescription(R.string.document_import_error_time_in_future_description)
                    }
                }
                importError = errorBuilder.build()
                addError(importError)
            }
    }

    private fun requestImportConsent(): Completable {
        return consentManager.initialize(application)
            .andThen(consentManager.requestConsentAndGetResult(ConsentManager.ID_IMPORT_DOCUMENT))
            .flatMapCompletable(consentManager::assertConsentApproved)
    }

    private fun addDocument(document: Document): Completable {
        return application.documentManager.redeemDocument(document)
            .andThen(application.documentManager.addDocument(document))
            .doOnComplete { hasAddedDocument = true }
            .andThen(update(addedDocument, ViewEvent(document)))
            .doOnSubscribe { removeError(importError) }
            .doOnError { throwable: Throwable? ->
                val errorBuilder = createErrorBuilder(throwable!!)
                    .withTitle(R.string.document_import_error_title)
                var outcomeUnknown = false
                if (throwable is DocumentVerificationException) {
                    outcomeUnknown = throwable.reason == DocumentVerificationException.Reason.OUTCOME_UNKNOWN
                }
                if (throwable is TestResultPositiveException || outcomeUnknown) {
                    errorBuilder
                        .withTitle(R.string.document_import_error_not_negative_title)
                        .withDescription(R.string.document_import_error_not_negative_description)
                } else if (throwable is DocumentAlreadyImportedException) {
                    errorBuilder.withDescription(R.string.document_import_error_already_imported_description)
                } else if (throwable is DocumentExpiredException) {
                    errorBuilder.withDescription(R.string.document_import_error_expired_description)
                }
                importError = errorBuilder.build()
                addError(importError)
            }
    }

    fun onAddCertificateViewDismissed() = updateAsSideEffect(documentAddedOnViewDismissed, ViewEvent(hasAddedDocument))

    override fun onFinishFlow() {
        dismissBottomSheet()
    }

}