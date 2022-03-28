package de.culture4life.luca.ui.account.postalcode

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager.Companion.ID_POSTAL_CODE_MATCHING
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.util.addTo
import de.culture4life.luca.whatisnew.WhatIsNewManager.Companion.ID_POSTAL_CODE_MESSAGE
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class PostalCodeViewModel(application: Application) : BaseViewModel(application) {

    private val consentManager = this.application.consentManager
    private val whatIsNewManager = this.application.whatIsNewManager
    private var toggleError: ViewError? = null

    val postalCodeMatchingStatus = MutableLiveData<Boolean>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    consentManager.initialize(application),
                    whatIsNewManager.initialize(application)
                )
            )
            .andThen(
                Completable.mergeArray(
                    updatePostalCodeMatchingImmediately(),
                    invoke(whatIsNewManager.markMessageAsSeen(ID_POSTAL_CODE_MESSAGE))
                )
            )
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepPostalCodeMatchingStatusUpdated()
        )
    }

    private fun keepPostalCodeMatchingStatusUpdated(): Completable {
        return consentManager.getConsentAndChanges(ID_POSTAL_CODE_MATCHING)
            .map { it.approved }
            .flatMapCompletable { update(postalCodeMatchingStatus, it) }
    }

    private fun updatePostalCodeMatchingImmediately(): Completable {
        return consentManager.getConsentAndChanges(ID_POSTAL_CODE_MATCHING)
            .firstOrError()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { postalCodeMatchingStatus.value = it.approved }
            .ignoreElement()
    }

    fun onPostalCodeMatchingToggled(enable: Boolean) {
        Completable.defer {
            if (enable) {
                // request consent each time, regardless if it has been approved before
                consentManager.requestConsent(ID_POSTAL_CODE_MATCHING)
            } else {
                // treat this as withdrawing consent
                consentManager.processConsentRequestResult(ID_POSTAL_CODE_MATCHING, false)
            }
        }
            .doOnSubscribe {
                removeError(toggleError)
                updateAsSideEffect(isLoading, true)
            }
            .doOnError {
                toggleError = ViewError.Builder(application)
                    .withCause(it)
                    .withResolveLabel(R.string.action_retry)
                    .withResolveAction(Completable.fromAction { onPostalCodeMatchingToggled(enable) })
                    .removeWhenShown()
                    .build()

                addError(toggleError)
            }
            .doFinally { updateAsSideEffect(isLoading, false) }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.d("Postal code matching toggled to %b", enable) },
                { Timber.w("Unable to toggle postal code matching: $it") }
            )
            .addTo(modelDisposable)
    }
}
