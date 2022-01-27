package de.culture4life.luca.ui.account.lucaconnect

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import timber.log.Timber

class LucaConnectViewModel(application: Application) : BaseViewModel(application) {

    private val connectManager = this.application.connectManager
    private var unEnrollmentError: ViewError? = null

    val enrollmentStatus = MutableLiveData<Boolean>()
    val openEnrollmentFlow = MutableLiveData<ViewEvent<Boolean>>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(connectManager.initialize(application))
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepEnrollmentStatusUpdated()
        )
    }

    private fun keepEnrollmentStatusUpdated(): Completable {
        return connectManager.getEnrollmentStatusAndChanges()
            .flatMapCompletable { update(enrollmentStatus, it) }
    }

    fun toggleLucaConnect(enable: Boolean) {
        if (enable) {
            invokeEnrollment()
        } else {
            invokeUnEnrollment()
        }
    }

    private fun invokeEnrollment() {
        updateAsSideEffect(openEnrollmentFlow, ViewEvent(true))
    }

    private fun invokeUnEnrollment() {
        connectManager.unEnroll()
            .doOnSubscribe {
                removeError(unEnrollmentError)
                updateAsSideEffect(isLoading, true)
            }
            .doOnError {
                unEnrollmentError = ViewError.Builder(application)
                    .withTitle(R.string.error_request_failed_title)
                    .withCause(it)
                    .withResolveLabel(R.string.action_retry)
                    .withResolveAction(Completable.fromAction { invokeUnEnrollment() })
                    .removeWhenShown()
                    .build()

                addError(unEnrollmentError)
            }
            .doFinally { updateAsSideEffect(isLoading, false) }
            .subscribe(
                { Timber.d("Un-enrolled") },
                { Timber.w("Unable to un-enroll: $it") }
            )
            .addTo(modelDisposable)
    }

}
