package de.culture4life.luca.ui.messages

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.messages.MessageDetailFragment.Companion.KEY_MESSAGE_LIST_ITEM
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class MessageDetailViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager
    private val dataAccessManager = this.application.dataAccessManager
    private val connectManager = this.application.connectManager

    val messageItem = MutableLiveData<MessageListItem>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    whatIsNewManager.initialize(application),
                    dataAccessManager.initialize(application),
                    connectManager.initialize(application)
                )
            )
    }

    fun onItemSeen(item: MessageListItem) {
        when (item) {
            is MessageListItem.NewsListItem -> whatIsNewManager.markMessageAsSeen(item.id)
            is MessageListItem.AccessedDataListItem -> dataAccessManager.markAsNotNew(item.id, item.warningLevel)
            is MessageListItem.LucaConnectListItem -> connectManager.markMessageAsRead(item.id)
        }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.d("Item marked as not new: %s", item) },
                { Timber.w("Unable to mark item as not new: %s", it.toString()) }
            ).addTo(modelDisposable)
    }

    override fun processArguments(arguments: Bundle?): Completable {
        return super.processArguments(arguments)
            .andThen(Maybe.fromCallable { arguments?.getSerializable(KEY_MESSAGE_LIST_ITEM) as MessageListItem })
            .flatMapCompletable { updateIfRequired(messageItem, it) }
            .doOnError { Timber.w("Could not get message list item from arguments: %s", it.toString()) }
            .onErrorComplete()
    }

}