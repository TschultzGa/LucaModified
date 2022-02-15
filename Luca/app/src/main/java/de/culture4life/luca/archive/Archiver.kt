package de.culture4life.luca.archive

import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class Archiver<T>(
    private val preferencesManager: PreferencesManager,
    private val key: String,
    private val clazz: Class<out ArchivedData<T>>,
    private var timestampHelper: TimestampHelper<T>
) {

    private var cachedData: Observable<T>? = null

    fun addData(data: T): Completable {
        return addData(data) { OVERRIDE_NONE }
    }

    fun addData(data: List<T>): Completable {
        return addData(data) { OVERRIDE_NONE }
    }

    fun addData(data: T, overrideFilter: Predicate<T>): Completable {
        return addData(arrayListOf(data), overrideFilter)
    }

    fun addData(data: List<T>, overrideFilter: Predicate<T>): Completable {
        return getData()
            .filter { !overrideFilter.test(it) }
            .mergeWith(Observable.fromIterable(data))
            .sorted { o1, o2 -> timestampHelper.compare(o1, o2) }
            .toList()
            .map(this::convertToArchivedData)
            .flatMapCompletable {
                preferencesManager.persist(key, it)
                    .doOnComplete { cachedData = Observable.fromIterable(it.getData()).cache() }
            }
    }

    fun getData(): Observable<T> {
        return Observable.defer {
            if (cachedData == null) {
                cachedData = restoreArchivedData().cache()
            }
            cachedData!!
        }
    }

    private fun restoreArchivedData(): Observable<T> {
        return preferencesManager.restoreIfAvailable(key, clazz)
            .map(ArchivedData<T>::getData)
            .defaultIfEmpty(ArrayList())
            .flatMapObservable { Observable.fromIterable(it) }
            // needed as old data may not be sorted
            .sorted { o1, o2 -> timestampHelper.compare(o1, o2) }
    }

    fun deleteDataAddedBefore(timestamp: Long): Completable {
        return deleteData { timestampHelper.getTimestamp(it) < timestamp }
    }

    @JvmOverloads
    fun deleteDataOlderThan(duration: Long = DEFAULT_ARCHIVE_DURATION): Completable {
        return deleteData { timestampHelper.getTimestamp(it) < TimeUtil.getCurrentMillis() - duration }
    }

    fun deleteData(filter: Predicate<T>): Completable {
        return getData()
            .filter { !filter.test(it) }
            .toList()
            .map(this::convertToArchivedData)
            .flatMapCompletable { preferencesManager.persist(key, it) }
            .doOnComplete(this::clearCachedData)
    }

    fun clearCachedData() {
        cachedData = null
    }

    private fun convertToArchivedData(data: List<T>): ArchivedData<T> {
        val archivedData = clazz.newInstance()
        archivedData.setData(data)
        return archivedData
    }

    companion object {
        const val OVERRIDE_ALL = true
        const val OVERRIDE_NONE = false
        private var DEFAULT_ARCHIVE_DURATION: Long = TimeUnit.DAYS.toMillis(28L)
    }

    fun interface TimestampHelper<T> {
        fun getTimestamp(data: T): Long
    }

    private fun TimestampHelper<T>.compare(data1: T, data2: T): Int {
        return compareValues(getTimestamp(data1), getTimestamp(data2))
    }

}
