package de.culture4life.luca.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.consent.ConsentManager.Companion.ID_TERMS_OF_SERVICE_LUCA_ID
import de.culture4life.luca.notification.LucaNotificationManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager
    private val consentManager = this.application.consentManager

    private val hasNewsMessages = MutableLiveData<Boolean>()
    private val hasDataAccessMessages = MutableLiveData<Boolean>()
    private val hasLucaConnectMessages = MutableLiveData<Boolean>()
    private val hasTermsOfServiceUpdateMessages = MutableLiveData<Boolean>()

    val hasNewMessages = MediatorLiveData<Boolean>().apply {
        fun combine() {
            value = (hasNewsMessages.value == true) ||
                (hasDataAccessMessages.value == true) ||
                (hasLucaConnectMessages.value == true) ||
                (hasTermsOfServiceUpdateMessages.value == true)
        }
        addSource(hasNewsMessages) { combine() }
        addSource(hasDataAccessMessages) { combine() }
        addSource(hasLucaConnectMessages) { combine() }
        addSource(hasTermsOfServiceUpdateMessages) { combine() }
    }

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(invokeDelayed(requestUpdatedTermsConsentIfRequired(), TERMS_OF_SERVICE_CONSENT_DELAY))
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepHasNewsMessagesUpdated(),
            keepHasDataAccessMessagesUpdated(),
            keepHasLucaConnectMessagesUpdated(),
            keepTermsOfServiceMessagesUpdated()
        )
    }

    private fun keepHasNewsMessagesUpdated(): Completable {
        return updateHasNewsMessages()
            .andThen(whatIsNewManager.getMessageUpdates())
            .debounce(100, TimeUnit.MILLISECONDS)
            .flatMapCompletable { updateHasNewsMessages() }
    }

    private fun keepHasDataAccessMessagesUpdated(): Completable {
        return dataAccessManager.observeNewNotificationsChanges()
            .flatMapCompletable { hasNewNotifications ->
                updateIfRequired(hasDataAccessMessages, hasNewNotifications)
            }
    }

    private fun keepHasLucaConnectMessagesUpdated(): Completable {
        val statusChanges = Observable.mergeArray(
            connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges(),
            connectManager.getHasUnreadMessagesStatusAndChanges()
        )
        return statusChanges.flatMapCompletable { updateHasLucaConnectMessages() }
    }

    private fun keepTermsOfServiceMessagesUpdated(): Completable {
        return consentManager.getConsentAndChanges(ID_TERMS_OF_SERVICE_LUCA_ID)
            .flatMapCompletable { update(hasTermsOfServiceUpdateMessages, !it.approved) }
    }

    private fun updateHasNewsMessages(): Completable {
        return whatIsNewManager.getAllMessages()
            .any { !it.seen && it.enabled }
            .flatMapCompletable { hasNewNews ->
                updateIfRequired(hasNewsMessages, hasNewNews)
            }
    }

    private fun updateHasLucaConnectMessages(): Completable {
        return Single.zip(
            connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges().first(false),
            connectManager.getHasUnreadMessagesStatusAndChanges().first(false)
        ) { isEnrollmentSupportedAgain, hasUnreadMessages ->
            isEnrollmentSupportedAgain || hasUnreadMessages
        }.flatMapCompletable { updateIfRequired(hasLucaConnectMessages, it) }
    }

    private fun requestUpdatedTermsConsentIfRequired(): Completable {
        return Completable.defer {
            if (LucaApplication.isRunningTests()) {
                Completable.complete()
            } else {
                with(consentManager) {
                    initialize(application)
                        .andThen(requestConsentIfRequired(ID_TERMS_OF_SERVICE_LUCA_ID))
                }
            }
        }
    }

    fun onNewIntent(intent: Intent) {
        with(LucaNotificationManager.getBundleFromIntentIfAvailable(intent)) {
            val action = LucaNotificationManager.getActionFromBundleIfAvailable(this)
            if (action == LucaNotificationManager.ACTION_NAVIGATE) {
                val deepLink = LucaNotificationManager.getDeepLinkFromBundleIfAvailable(this) ?: return
                navigationController?.navigate(deepLink)
            }
        }
    }

    companion object {
        private const val TERMS_OF_SERVICE_CONSENT_DELAY = 500L // should be shown shortly after app UI has loaded but not block the initialization
    }
}
