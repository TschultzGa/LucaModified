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

class MessagesViewModel(application: Application) : BaseViewModel(application) {

    private val historyManager = this.application.historyManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager

    private val lucaConnectMessageItems = MutableLiveData<List<MessageListItem>>()
    private val dataAccessMessageItems = MutableLiveData<List<MessageListItem>>()
    val messageItems = MediatorLiveData<List<MessageListItem>>().apply {
        fun combine() {
            val lucaConnect = lucaConnectMessageItems.value ?: emptyList()
            val dataAccess = dataAccessMessageItems.value ?: emptyList()
            value = lucaConnect
                .plus(dataAccess)
                .sortedByDescending { it.timestamp }
        }
        addSource(lucaConnectMessageItems) { combine() }
        addSource(dataAccessMessageItems) { combine() }
    }

    val connectEnrollmentStatus = MutableLiveData<Boolean>()
    val connectEnrollmentSupportedStatus = MutableLiveData<Boolean>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    historyManager.initialize(application),
                    dataAccessManager.initialize(application),
                    connectManager.initialize(application)
                )
            )
            .andThen(invokeMessagesUpdate())

    }

    private fun invokeMessagesUpdate(): Completable {
        return Completable.fromAction {
            Completable.mergeArray(
                updateAccessedDataItems(),
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

    private fun updateAccessedDataItems(): Completable {
        return dataAccessManager.orRestoreAccessedData
            .flattenAsObservable { it.traceData }
            .flatMapSingle { dataAccessManager.createMessagesListItem(it) }
            .toList()
            .flatMapCompletable { items -> update(dataAccessMessageItems, items) }
    }

    private fun updateLucaConnectItems(): Completable {
        return connectManager.getMessages()
            .map(MessageListItem::LucaConnectListItem)
            .toList()
            .flatMapCompletable { items -> update(lucaConnectMessageItems, items) }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepConnectEnrollmentStatusUpdated(),
            keepConnectEnrollmentSupportedUpdated()
        )
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
        return connectManager
            .getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .first(false)
    }
}