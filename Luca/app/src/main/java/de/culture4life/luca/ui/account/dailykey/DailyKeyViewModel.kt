package de.culture4life.luca.ui.account.dailykey

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.crypto.DailyPublicKeyData
import de.culture4life.luca.crypto.KeyIssuerData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.util.addTo
import de.culture4life.luca.util.getReadableTime
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class DailyKeyViewModel(app: Application) : BaseViewModel(app) {

    val dailyPublicKeyLiveData: MutableLiveData<DailyPublicKeyData> = MutableLiveData()
    val keyIssuerLiveData: MutableLiveData<KeyIssuerData> = MutableLiveData()
    val hasVerifiedDailyPublicKey: MutableLiveData<Boolean> = MutableLiveData(false)

    private var dailyPublicKeyData: DailyPublicKeyData? = null
    private var keyIssuerData: KeyIssuerData? = null

    private var verificationError: ViewError? = null

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(invokeUpdateDailyKeyData())
    }

    private fun invokeUpdateDailyKeyData(): Completable {
        return Completable.fromAction {
            updateDailyKeyDataIfRequired()
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(modelDisposable)
        }
    }

    private fun updateDailyKeyDataIfRequired(): Completable {
        return Completable.defer {
            if (dailyPublicKeyLiveData.value != null) {
                Completable.complete()
            }
            application.cryptoManager.getDailyPublicKey()
                .doOnSuccess {
                    this.dailyPublicKeyData = it
                    updateAsSideEffectIfRequired(dailyPublicKeyLiveData, dailyPublicKeyData)
                }
                .ignoreElement()
                .andThen(application.cryptoManager.restoreDailyPublicKeyIssuerData()
                    .filter { it.id == dailyPublicKeyData!!.issuerId }
                    .doOnSuccess {
                        this.keyIssuerData = it
                        updateAsSideEffectIfRequired(keyIssuerLiveData, keyIssuerData)
                        updateAsSideEffectIfRequired(hasVerifiedDailyPublicKey, true)
                    })
                .ignoreElement()
                .doOnSubscribe {
                    removeError(verificationError)
                    updateAsSideEffectIfRequired(isLoading, true)
                }
                .doOnError {
                    updateAsSideEffectIfRequired(hasVerifiedDailyPublicKey, false)
                    verificationError = ViewError.Builder(application)
                        .withCause(it)
                        .withTitle(R.string.daily_key_update_error_title)
                        .withResolveLabel(R.string.action_retry)
                        .withResolveAction(updateDailyKeyDataIfRequired())
                        .removeWhenShown()
                        .build()
                    addError(verificationError)
                }
                .doFinally {
                    updateAsSideEffect(isLoading, false)
                }
        }
    }

    fun exportDailyKey(uri: Single<Uri>) {
        val content = Single.fromCallable {
            StringBuilder()
                .append("Daily public key:\n")
                .append(dailyPublicKeyData?.encodedPublicKey + "\n\n")
                .append("Created at:\n")
                .append(application.getReadableTime(dailyPublicKeyData?.creationTimestamp ?: 0) + "\n\n")
                .append("Issued by:\n")
                .append(keyIssuerData?.name + "\n\n")
                .append("Issuer signing key:\n")
                .append(keyIssuerData?.encodedPublicKey)
                .toString()
        }
        export(uri, content)
    }

}