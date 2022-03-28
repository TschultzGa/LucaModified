package de.culture4life.luca.ui.messages

import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MessagesViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager

    private val newsMessageItems = MutableLiveData<List<MessageListItem>>()
    private val lucaConnectMessageItems = MutableLiveData<List<MessageListItem>>()
    private val dataAccessMessageItems = MutableLiveData<List<MessageListItem>>()
    val messageItems = MediatorLiveData<List<MessageListItem>>().apply {
        fun combine() {
            val news = newsMessageItems.value ?: emptyList()
            val dataAccess = dataAccessMessageItems.value ?: emptyList()
            val lucaConnect = lucaConnectMessageItems.value ?: emptyList()
            value = news
                .plus(dataAccess)
                .plus(lucaConnect)
                .sortedByDescending { it.timestamp }
        }
        addSource(newsMessageItems) { combine() }
        addSource(dataAccessMessageItems) { combine() }
        addSource(lucaConnectMessageItems) { combine() }
    }
    val connectEnrollmentStatus = MutableLiveData<Boolean>()
    val connectEnrollmentSupportedStatus = MutableLiveData<Boolean>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    whatIsNewManager.initialize(application),
                    dataAccessManager.initialize(application),
                    connectManager.initialize(application)
                )
            )
            .andThen(invokeMessagesUpdate())
    }

    private fun invokeMessagesUpdate(): Completable {
        return Completable.fromAction {
            Completable.mergeArray(
                updateNewsItems(),
                updateDataAccessItems(),
                updateLucaConnectItems()
            )
                .doOnSubscribe { updateAsSideEffect(isLoading, true) }
                .doFinally { updateAsSideEffect(isLoading, false) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { Timber.i("Updated messages") },
                    { throwable -> Timber.w("Unable to update messages: %s", throwable.toString()) }
                )
                .addTo(modelDisposable)
        }
    }

    private fun updateNewsItems(): Completable {
        return whatIsNewManager.getAllMessages()
            .filter { it.enabled }
            .map { MessageListItem.NewsListItem(it) }
            .toList()
            .flatMapCompletable { update(newsMessageItems, it) }
    }

    private fun updateDataAccessItems(): Completable {
        return dataAccessManager.previouslyAccessedTraceData
            .flatMapSingle { accessedTraceData ->
                dataAccessManager.getNotificationTexts(accessedTraceData)
                    .map { MessageListItem.AccessedDataListItem(accessedTraceData, it) }
            }
            .toList()
            .flatMapCompletable { update(dataAccessMessageItems, it) }
    }

    private fun updateLucaConnectItems(): Completable {
        return connectManager.getMessages()
            .map(MessageListItem::LucaConnectListItem)
            .toList()
            .flatMapCompletable { update(lucaConnectMessageItems, it) }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepHasNewsMessagesUpdated(),
            keepConnectEnrollmentStatusUpdated(),
            keepConnectEnrollmentSupportedUpdated()
        )
    }

    private fun keepHasNewsMessagesUpdated(): Completable {
        return whatIsNewManager.getMessageUpdates()
            .debounce(100, TimeUnit.MILLISECONDS)
            .flatMapCompletable { updateNewsItems() }
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
}
