package de.culture4life.luca.ui.qrcode

import android.webkit.URLUtil
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.document.*
import de.culture4life.luca.meeting.MeetingManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.dialog.BaseDialogContent
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import timber.log.Timber

class DocumentBarcodeProcessor(
    private val application: LucaApplication,
    private val baseViewModel: BaseViewModel
) {

    private val documentManager = application.documentManager
    private val consentManager = application.consentManager
    private var importError: ViewError? = null

    fun process(barcodeData: String): Completable {
        return parseAndValidateDocument(barcodeData)
            .flatMapCompletable {
                requestImportConsent()
                    .observeOn(Schedulers.io())
                    .andThen(addDocumentIfBirthDatesMatch(it))
            }
    }

    private fun parseAndValidateDocument(encodedDocument: String): Single<Document> {
        return documentManager.parseAndValidateEncodedDocument(encodedDocument)
            .doOnSubscribe { Timber.d("Attempting to parse encoded document: $encodedDocument") }
            .doOnSuccess {
                Timber.d("Parsed document: $it")
                baseViewModel.removeError(importError)
            }
            .doOnError { throwable ->
                Timber.w("Unable to parse document: $throwable")
                val errorBuilder: ViewError.Builder = baseViewModel.createErrorBuilder(throwable)
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
                baseViewModel.addError(importError!!)
            }
    }

    private fun addDocumentIfBirthDatesMatch(document: Document): Completable {
        return application.documentManager.getOrRestoreDocuments()
            .toList()
            .flatMapCompletable { storedDocuments ->
                if (hasNonMatchingBirthDate(document, storedDocuments)) {
                    showDocumentImportBirthdayMismatchDialog()
                } else {
                    Completable.complete()
                }
            }
            .andThen(addDocument(document))
    }

    internal fun hasNonMatchingBirthDate(document: Document, storedDocuments: List<Document>): Boolean {
        if (document.type != Document.TYPE_VACCINATION) {
            Timber.d("skip matching birthday check for type: ${document.type}")
            return false
        }
        for (myLucaListItem in storedDocuments) {
            if (myLucaListItem.type == Document.TYPE_VACCINATION) {
                if (hasNonMatchingBirthDate(document, myLucaListItem)) {
                    return true
                }
            }
        }
        return false
    }

    internal fun hasNonMatchingBirthDate(document1: Document, document2: Document): Boolean {
        val dateOfBirth1 = TimeUtil.getStartOfDayTimestamp(document1.dateOfBirth).blockingGet()
        val dateOfBirth2 = TimeUtil.getStartOfDayTimestamp(document2.dateOfBirth).blockingGet()
        return document2.firstName == document1.firstName && document2.lastName == document1.lastName && dateOfBirth2 != dateOfBirth1
    }

    private fun showDocumentImportBirthdayMismatchDialog(): Completable {
        return Single.fromCallable { BehaviorSubject.create<Boolean>() }
            .flatMapCompletable { subject ->
                showDocumentImportBirthdayMismatchDialog(subject)
                    .andThen(subject.firstOrError().ignoreElement())
            }
    }

    private fun showDocumentImportBirthdayMismatchDialog(subject: BehaviorSubject<Boolean>): Completable {
        return Completable.fromCallable {
            baseViewModel.showDialog(
                BaseDialogContent(
                    title = R.string.document_import_error_different_birth_dates_title,
                    message = R.string.document_import_error_different_birth_dates_description,
                    positiveText = R.string.action_ok,
                    positiveCallback = { _, _ -> subject.onComplete() },
                    neutralText = R.string.document_import_anyway_action,
                    neutralCallback = { _, _ -> subject.onNext(true) }
                )
            )
        }
    }

    private fun addDocument(document: Document): Completable {
        return application.documentManager.redeemDocument(document)
            .andThen(application.documentManager.addDocument(document))
            .doOnSubscribe { baseViewModel.removeError(importError) }
            .doOnError { throwable: Throwable? ->
                val errorBuilder = baseViewModel.createErrorBuilder(throwable!!)
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
                baseViewModel.addError(importError)
            }
    }

    private fun requestImportConsent(): Completable {
        return consentManager.initialize(application)
            .andThen(consentManager.requestConsentAndGetResult(ConsentManager.ID_IMPORT_DOCUMENT))
            .flatMapCompletable(consentManager::assertConsentApproved)
    }
}
