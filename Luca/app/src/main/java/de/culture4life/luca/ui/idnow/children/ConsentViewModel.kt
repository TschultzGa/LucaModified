package de.culture4life.luca.ui.idnow.children

import android.app.Application
import de.culture4life.luca.R
import de.culture4life.luca.attestation.AttestationException
import de.culture4life.luca.network.LucaApiException
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.getCauseIfAvailable
import de.culture4life.luca.util.getMessagesFromThrowableAndCauses
import io.reactivex.rxjava3.core.Completable

class ConsentViewModel(app: Application) : BaseFlowChildViewModel(app) {

    private val notificationManager = application.notificationManager
    private val idNowManager = application.idNowManager

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    notificationManager.initialize(application),
                    idNowManager.initialize(application)
                )
            )
    }

    fun onConsentGiven() {
        invoke(initiateEnrollment()).subscribe()
    }

    private fun initiateEnrollment(): Completable {
        return idNowManager.initiateEnrollment()
            .doOnComplete { sharedViewModel!!.navigateToNext() }
            .doOnError {
                val enrollmentErrorBuilder = ViewError.Builder(application)
                    .withCause(it)
                    .withTitle(R.string.luca_id_enrollment_error_message_title)
                    .withDescription(getDescription(it))
                    .withResolveLabel(R.string.action_retry)
                    .withResolveAction(invoke(initiateEnrollment()))
                    .isExpected()
                    .removeWhenShown()

                addError(enrollmentErrorBuilder.build())
            }
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
    }

    private fun getDescription(throwable: Throwable): String? {
        return when (throwable) {
            is AttestationException -> {
                val apiException = throwable.getCauseIfAvailable(LucaApiException::class.java)
                if (apiException != null && (apiException.message?.contains("KEY_ATTESTATION") == true || apiException.message?.contains("SAFETY_NET") == true)) {
                    return application.getString(R.string.error_feature_unavailable, throwable.getMessagesFromThrowableAndCauses())
                }
                application.getString(R.string.luca_id_enrollment_error_message_description)
            }
            else -> {
                null
            }
        }
    }
}
