package de.culture4life.luca.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.notification.LucaNotificationManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager

    private val hasNewsMessages = MutableLiveData<Boolean>()
    private val hasDataAccessMessages = MutableLiveData<Boolean>()
    private val hasLucaConnectMessages = MutableLiveData<Boolean>()

    val hasNewMessages = MediatorLiveData<Boolean>().apply {
        fun combine() {
            value = (hasNewsMessages.value == true)
                    || (hasDataAccessMessages.value == true)
                    || (hasLucaConnectMessages.value == true)
        }
        addSource(hasNewsMessages) { combine() }
        addSource(hasDataAccessMessages) { combine() }
        addSource(hasLucaConnectMessages) { combine() }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepHasNewsMessagesUpdated(),
            keepHasDataAccessMessagesUpdated(),
            keepHasLucaConnectMessagesUpdated()
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

    fun onNewIntent(intent: Intent) {
        with(LucaNotificationManager.getBundleFromIntentIfAvailable(intent)) {
            val action = LucaNotificationManager.getActionFromBundleIfAvailable(this)
            if (action == LucaNotificationManager.ACTION_NAVIGATE) {
                val destination = LucaNotificationManager.getDestinationFromBundleIfAvailable(this)!!
                if (!isCurrentDestinationId(destination)) {
                    navigationController!!.navigate(destination)
                }
            }
        }
    }

}