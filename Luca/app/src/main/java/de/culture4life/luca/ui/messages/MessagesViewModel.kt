package de.culture4life.luca.ui.messages

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.TimeUnit

class MessagesViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager
    private val consentManager = this.application.consentManager

    private val newsMessageItems = MutableLiveData<List<MessageListItem>>()
    private val lucaConnectMessageItems = MutableLiveData<List<MessageListItem>>()
    private val dataAccessMessageItems = MutableLiveData<List<MessageListItem>>()
    private val missingConsentItems = MutableLiveData<List<MessageListItem>>()
    val messageItems = MediatorLiveData<List<MessageListItem>>().apply {
        fun combine() {
            val news = newsMessageItems.value ?: emptyList()
            val dataAccess = dataAccessMessageItems.value ?: emptyList()
            val lucaConnect = lucaConnectMessageItems.value ?: emptyList()
            val missingConsents = missingConsentItems.value ?: emptyList()
            value = news
                .plus(dataAccess)
                .plus(lucaConnect)
                .plus(missingConsents)
                .sortedByDescending { it.timestamp }
        }
        addSource(newsMessageItems) { combine() }
        addSource(dataAccessMessageItems) { combine() }
        addSource(lucaConnectMessageItems) { combine() }
        addSource(missingConsentItems) { combine() }
    }
    val connectEnrollmentStatus = MutableLiveData<Boolean>()
    val connectEnrollmentSupportedStatus = MutableLiveData<Boolean>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    whatIsNewManager.initialize(application),
                    dataAccessManager.initialize(application),
                    connectManager.initialize(application),
                    consentManager.initialize(application)
                )
            )
            .andThen(invoke(updateItems()))
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepNewsItemsUpdated(),
            keepConnectEnrollmentStatusUpdated(),
            keepConnectEnrollmentSupportedUpdated(),
            keepMissingConsentItemsUpdated()
        )
    }

    private fun updateItems(): Completable {
        return Completable.mergeArray(
            updateNewsItems(),
            updateDataAccessItems(),
            updateLucaConnectItems(),
            updateMissingConsentItems()
        )
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
    }

    /*
        What's new
     */

    private fun updateNewsItems(): Completable {
        return whatIsNewManager.getAllMessages()
            .filter { it.enabled }
            .map { MessageListItem.NewsListItem(it) }
            .toList()
            .flatMapCompletable { update(newsMessageItems, it) }
    }

    private fun keepNewsItemsUpdated(): Completable {
        return whatIsNewManager.getMessageUpdates()
            .debounce(WHAT_IS_NEW_MESSAGE_UPDATE_DELAY, TimeUnit.MILLISECONDS)
            .flatMapCompletable { updateNewsItems() }
    }

    /*
        luca Connect
     */

    private fun updateLucaConnectItems(): Completable {
        return connectManager.getMessages()
            .map(MessageListItem::LucaConnectListItem)
            .toList()
            .flatMapCompletable { update(lucaConnectMessageItems, it) }
    }

    private fun keepConnectEnrollmentStatusUpdated(): Completable {
        return connectManager.getEnrollmentStatusAndChanges()
            .flatMapCompletable { updateIfRequired(connectEnrollmentStatus, it) }
    }

    private fun keepConnectEnrollmentSupportedUpdated(): Completable {
        return connectManager.getEnrollmentSupportedStatusAndChanges()
            .flatMapCompletable { updateIfRequired(connectEnrollmentSupportedStatus, it) }
    }

    fun shouldShowLucaConnectEnrollmentAutomatically(): Single<Boolean> {
        return connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .first(false)
    }

    /*
        Missing consent
     */

    private fun updateMissingConsentItems(): Completable {
        return Observable.fromIterable(REQUIRED_CONSENT_IDS)
            .flatMapSingle { consentManager.getConsent(it) }
            .filter { !it.approved }
            .map { createMissingConsentItem(it.id) }
            .toList()
            .flatMapCompletable { update(missingConsentItems, it) }
    }

    private fun createMissingConsentItem(consentId: String): MessageListItem {
        return when (consentId) {
            ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID -> MessageListItem.MissingConsentItem(
                id = consentId,
                title = application.getString(R.string.notification_terms_update_title),
                message = application.getString(R.string.notification_terms_update_description),
                detailedMessage = application.getString(R.string.notification_terms_update_description),
                timestamp = TimeUtil.getCurrentMillis(), // always display as last received notification
                isNew = true // always display as new received notification
            )
            else -> throw IllegalArgumentException("Unknown consent ID: $consentId")
        }
    }

    private fun keepMissingConsentItemsUpdated(): Completable {
        return Observable.merge(
            Observable.fromIterable(REQUIRED_CONSENT_IDS)
                .map { consentManager.getConsentAndChanges(it).skip(1) }
        ).flatMapCompletable { updateMissingConsentItems() }
    }

    fun onMissingConsentItemClicked(consentId: String) {
        invoke(consentManager.requestConsent(consentId)).subscribe()
    }

    /*
        Data access
     */

    private fun updateDataAccessItems(): Completable {
        return dataAccessManager.previouslyAccessedTraceData
            .flatMapSingle { accessedTraceData ->
                dataAccessManager.getNotificationTexts(accessedTraceData)
                    .map { MessageListItem.AccessedDataListItem(accessedTraceData, it) }
            }
            .toList()
            .flatMapCompletable { update(dataAccessMessageItems, it) }
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val WHAT_IS_NEW_MESSAGE_UPDATE_DELAY = 100L
        val REQUIRED_CONSENT_IDS = listOf(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID)
    }
}
