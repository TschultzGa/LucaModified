package de.culture4life.luca.ui.history

import android.app.Application
import android.os.Bundle
import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.dataaccess.AccessedTraceData
import de.culture4life.luca.dataaccess.DataAccessManager
import de.culture4life.luca.history.*
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.accesseddata.AccessedDataDetailFragment
import de.culture4life.luca.ui.accesseddata.AccessedDataFragment
import de.culture4life.luca.ui.accesseddata.AccessedDataListItem
import de.culture4life.luca.ui.history.HistoryListItem.*
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.addTo
import de.culture4life.luca.util.toDateTime
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class HistoryViewModel(application: Application) : BaseViewModel(application) {

    private var historyManager: HistoryManager = this.application.historyManager
    private val dataAccessManager: DataAccessManager = this.application.dataAccessManager
    private var checkInManager: CheckInManager = this.application.checkInManager

    private val tracingTanEvent = MutableLiveData<ViewEvent<String>>()
    private val newAccessedData = MutableLiveData<ViewEvent<List<AccessedTraceData>>>()
    private var dataSharingError: ViewError? = null

    private val historyItems = MutableLiveData<List<HistoryListItem>>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    historyManager.initialize(application),
                    dataAccessManager.initialize(application)
                )
            )
            .andThen(invokeHistoryUpdate())
            .andThen(invokeNewAccessedDataUpdate())
    }

    private fun invokeHistoryUpdate(): Completable {
        return Completable.fromAction {
            modelDisposable.add(
                updateHistoryItems()
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Timber.i("Updated history") },
                        { Timber.w("Unable to update history: %s", it.toString()) }
                    )
            )
        }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            observeHistoryChanges()
        )
    }

    private fun observeHistoryChanges(): Completable {
        return historyManager.newItems
            .throttleLatest(1, TimeUnit.SECONDS)
            .switchMapCompletable { updateHistoryItems() }
    }

    private fun updateHistoryItems(): Completable {
        return loadHistoryItems()
            .toList()
            .flatMapCompletable { items: List<HistoryListItem> -> update(historyItems, items) }
    }

    private fun loadHistoryItems(): Observable<HistoryListItem> {
        // get all available items
        val cachedItems = historyManager.items.cache()

        // find items that belong to each other
        val cachedStartAndEndItems = cachedItems
            .flatMapMaybe { endItem: HistoryItem ->
                getHistoryStartItem(endItem, cachedItems)
                    .map { startItem: HistoryItem -> Pair(startItem, endItem) }
            }
            .cache()

        // find items that don't belong to another item
        val cachedSingleItems = cachedStartAndEndItems
            .flatMap { historyItemPair: Pair<HistoryItem, HistoryItem> ->
                Observable.just(historyItemPair.first, historyItemPair.second)
            }
            .toList()
            .flatMapObservable { startAndEndItemsList: List<HistoryItem> ->
                cachedItems.filter { historyItem: HistoryItem -> !startAndEndItemsList.contains(historyItem) }
            }

        // convert and merge items
        return Observable.mergeArray(
            cachedStartAndEndItems
                .flatMapMaybe(this::createHistoryViewItem),
            cachedSingleItems
                .flatMapMaybe(this::createHistoryViewItem)
        ).sorted { item1, item2 -> item2.timestamp.compareTo(item1.timestamp) }
    }

    /**
     * Attempts to find the start item for the specified end item.
     *
     * @param endItem      the item to find the matching start item for
     * @param historyItems all available items, sorted by timestamp (descending)
     */
    private fun getHistoryStartItem(endItem: HistoryItem, historyItems: Observable<HistoryItem>): Maybe<HistoryItem> {
        return Single.just(endItem)
            .filter {
                it.type == HistoryItem.TYPE_CHECK_OUT || it.type == HistoryItem.TYPE_MEETING_ENDED
            }
            .flatMap {
                Maybe.fromCallable {
                    // get the matching start item type
                    if (endItem.type == HistoryItem.TYPE_CHECK_OUT) {
                        HistoryItem.TYPE_CHECK_IN
                    } else {
                        HistoryItem.TYPE_MEETING_STARTED
                    }
                }.flatMap { startItemType ->
                    historyItems.filter { historyItem: HistoryItem -> historyItem.type == startItemType }
                        .filter { historyItem: HistoryItem -> historyItem.timestamp < endItem.timestamp }
                        .firstElement()
                }
            }
    }

    private fun createHistoryViewItem(historyItemPair: Pair<HistoryItem, HistoryItem>): Maybe<HistoryListItem> {
        return Maybe.zip(createHistoryViewItem(historyItemPair.first), createHistoryViewItem(historyItemPair.second)) { start, end ->
            end.time = TimeUtil.getReadableDateTimeDifference(application, start.timestamp.toDateTime(), end.timestamp.toDateTime())
            end.time = application.getString(R.string.history_time_merged, start.time, end.time)
            if (start is CheckInListItem && end is CheckOutListItem) {
                end.isContactDataMandatory = start.isContactDataMandatory
            }
            end
        }
    }

    private fun createHistoryViewItem(historyItem: HistoryItem): Maybe<HistoryListItem> {
        return Single.just(historyItem)
            .filter { HistoryListItem.canHandle(it) }
            .flatMap {
                Maybe.fromCallable {
                    when (historyItem.type) {
                        HistoryItem.TYPE_CHECK_IN -> CheckInListItem(application, historyItem as CheckInItem)
                        HistoryItem.TYPE_CHECK_OUT -> CheckOutListItem(
                            application,
                            historyItem as CheckOutItem,
                            dataAccessManager
                        )
                        HistoryItem.TYPE_MEETING_STARTED -> MeetingStartedListItem(application, historyItem)
                        HistoryItem.TYPE_MEETING_ENDED -> MeetingEndedListItem(
                            application,
                            historyItem as MeetingEndedItem
                        )
                        else -> DataSharedListItem(
                            application,
                            historyItem as DataSharedItem
                        )
                    }
                }
            }
            .doOnError { Timber.w("Unable to create history view item for %s: %s", historyItem, it.toString()) }
            .onErrorComplete()
    }

    private fun invokeNewAccessedDataUpdate(): Completable {
        return Completable.fromAction {
            modelDisposable.add(
                showNewAccessedDataIfAvailable()
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Timber.i("Updated accessed data") },
                        { Timber.w("Unable to update accessed data: %s", it.toString()) }
                    )
            )
        }
    }

    private fun showNewAccessedDataIfAvailable(): Completable {
        return dataAccessManager.accessedTraceDataNotYetInformedAbout
            .toList()
            .filter { accessedTraceData: List<AccessedTraceData?> -> accessedTraceData.isNotEmpty() }
            .flatMapCompletable { accessedTraceData: List<AccessedTraceData> ->
                informAboutDataAccess(accessedTraceData)
            }
    }

    private fun informAboutDataAccess(accessedTraceData: List<AccessedTraceData>): Completable {
        return update(newAccessedData, ViewEvent(accessedTraceData))
            .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout())
    }

    fun onShareHistoryRequested(days: Int) {
        Timber.d("onShareHistoryRequested() called with: days = [%s]", days)
        modelDisposable.add(
            application.registrationManager
                .transferUserData(days)
                .doOnSubscribe {
                    updateAsSideEffect(isLoading, true)
                    removeError(dataSharingError)
                }
                .map { obj: String -> obj.uppercase(Locale.getDefault()) }
                .map { tracingTan: String ->
                    if (tracingTan.contains("-")) {
                        return@map tracingTan
                    }
                    val tracingTanBuilder = StringBuilder(tracingTan)
                    for (i in 1 until tracingTan.length / TAN_CHARS_PER_SECTION) {
                        val hyphenPosition = tracingTanBuilder.lastIndexOf("-") + TAN_CHARS_PER_SECTION + 1
                        tracingTanBuilder.insert(hyphenPosition, "-")
                    }
                    tracingTanBuilder.toString()
                }
                .flatMapCompletable { tracingTan: String ->
                    Completable.mergeArray(
                        update(tracingTanEvent, ViewEvent(tracingTan)),
                        historyManager.addDataSharedItem(tracingTan, days)
                    )
                }
                .doOnError { throwable: Throwable? ->
                    dataSharingError = createErrorBuilder(throwable!!)
                        .withTitle(R.string.error_request_failed_title)
                        .withResolveAction(Completable.fromAction { onShareHistoryRequested(days) })
                        .withResolveLabel(R.string.action_retry)
                        .build()
                    addError(dataSharingError)
                }
                .doFinally { updateAsSideEffect(isLoading, false) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { Timber.d("Received data sharing TAN") },
                    { Timber.w("Unable to get data sharing TAN: %s", it.toString()) }
                )
        )
    }

    @JvmOverloads
    fun onShowAccessedDataRequested(
        accessedTraceData: List<AccessedTraceData> = ArrayList(),
        warningLevelFilter: Int = HistoryFragment.NO_WARNING_LEVEL_FILTER
    ) {
        val bundle = Bundle()
        val filteredData = filterTraceData(accessedTraceData, warningLevelFilter)
        if (filteredData.isEmpty()) {
            return
        }
        val firstItem = filteredData[0]
        if (filteredData.size == 1) {
            dataAccessManager.getNotificationTexts(firstItem)
                .map { AccessedDataListItem.from(getApplication(), firstItem, it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe { dataListItem: AccessedDataListItem ->
                    if (isCurrentDestinationId(R.id.historyFragment)) {
                        bundle.putSerializable(AccessedDataDetailFragment.KEY_ACCESSED_DATA_LIST_ITEM, dataListItem)
                        navigationController!!.navigate(R.id.action_historyFragment_to_accessedDataDetailFragment, bundle)
                    }
                }
                .addTo(modelDisposable)
        } else {
            bundle.putString(AccessedDataFragment.KEY_TRACE_ID, firstItem.traceId)
            bundle.putInt(HistoryFragment.KEY_WARNING_LEVEL_FILTER, warningLevelFilter)
            navigationController!!.navigate(R.id.action_historyFragment_to_accessedDataFragment, bundle)
        }
    }

    fun onDeleteSelectedHistoryListItemsRequested() {
        Flowable.fromIterable(getHistoryItems().value!!)
            .filter { it.isSelectedForDeletion && !HistoryListItem.isContactDataMandatory(it) }
            .flatMapCompletable { item ->
                val relatedId = item.relatedId
                if (item is MeetingStartedListItem || item is MeetingEndedListItem) {
                    checkInManager.deleteCheckInLocally(relatedId)
                } else {
                    checkInManager.deleteCheckInFromBackend(relatedId)
                        .onErrorResumeNext { throwable: Throwable ->
                            if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND)) {
                                // 403: incorrect signature, no way to recover
                                // 404: relatedId not found in backend, maybe already deleted
                                checkInManager.deleteCheckInLocally(relatedId)
                            } else {
                                Completable.error(throwable)
                            }
                        }
                }
            }
            .doOnError {
                val viewError = ViewError.Builder(application)
                    .withCause(it)
                    .removeWhenShown()
                addError(viewError.build())
            }
            .andThen(invokeHistoryUpdate())
            .subscribe(
                { Timber.v("Deleted selected history list items") },
                { Timber.w("Unable to delete historyListItems: %s", it.toString()) }
            )
    }

    fun getTracingTanEvent(): LiveData<ViewEvent<String>> = tracingTanEvent

    fun getNewAccessedData(): LiveData<ViewEvent<List<AccessedTraceData>>> = newAccessedData

    fun getHistoryItems(): LiveData<List<HistoryListItem>> = historyItems

    companion object {

        private const val TAN_CHARS_PER_SECTION = 4

        fun filterHistoryListItems(items: List<HistoryListItem>, warningLevelFilter: Int): List<HistoryListItem> {
            return if (warningLevelFilter != HistoryFragment.NO_WARNING_LEVEL_FILTER) {
                items.stream()
                    .filter { it is CheckOutListItem && it.containsWarningLevel(warningLevelFilter) }
                    .map { filterAccessedTraceData(it as CheckOutListItem, warningLevelFilter) }
                    .collect(Collectors.toList())
            } else {
                items
            }
        }

        private fun filterAccessedTraceData(item: CheckOutListItem, warningLevelFilter: Int): HistoryListItem {
            item.accessedTraceData = item.accessedTraceData.stream()
                .filter { traceData: AccessedTraceData -> traceData.warningLevel == warningLevelFilter }
                .collect(Collectors.toList())
            return item
        }

        private fun filterTraceData(items: List<AccessedTraceData>, warningLevelFilter: Int): List<AccessedTraceData> {
            return if (warningLevelFilter != HistoryFragment.NO_WARNING_LEVEL_FILTER) {
                items.stream()
                    .filter { item: AccessedTraceData -> item.warningLevel == warningLevelFilter }
                    .collect(Collectors.toList())
            } else {
                items
            }
        }
    }
}
