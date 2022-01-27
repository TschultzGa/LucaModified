package de.culture4life.luca.ui

import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

class MainViewModel(application: Application) : BaseViewModel(application) {

    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager

    private val hasDataAccessMessages = MutableLiveData<Boolean>()
    private val hasLucaConnectMessages = MutableLiveData<Boolean>()

    val hasNewMessages = MediatorLiveData<Boolean>().apply {
        fun combine() {
            value = (hasDataAccessMessages.value == true) || (hasLucaConnectMessages.value == true)
        }
        addSource(hasDataAccessMessages) { combine() }
        addSource(hasLucaConnectMessages) { combine() }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepHasDataAccessMessagesUpdated(),
            keepHasLucaConnectMessagesUpdated()
        )
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

    private fun updateHasLucaConnectMessages(): Completable {
        return Single.zip(
            connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges().first(false),
            connectManager.getHasUnreadMessagesStatusAndChanges().first(false),
            { isEnrollmentSupportedAgain, hasUnreadMessages ->
                isEnrollmentSupportedAgain || hasUnreadMessages
            }
        ).flatMapCompletable { updateIfRequired(hasLucaConnectMessages, it) }
    }

}