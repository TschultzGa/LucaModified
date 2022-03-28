package de.culture4life.luca.genuinity

import android.content.Context
import de.culture4life.luca.Manager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs

open class GenuinityManager(
    val preferencesManager: PreferencesManager,
    val networkManager: NetworkManager
) : Manager() {

    private var timestampOffset: Long? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context)
        ).andThen(invokeServerTimeOffsetUpdateIfRequired())
    }

    private fun invokeServerTimeOffsetUpdateIfRequired(): Completable {
        return Completable.fromAction {
            if (timestampOffset == null) {
                invokeServerTimeOffsetUpdate()
            }
        }
    }

    fun invokeServerTimeOffsetUpdate(): Completable {
        return invokeDelayed(fetchServerTimeOffset().ignoreElement(), TimeUnit.SECONDS.toMillis(3))
    }

    fun assertIsGenuineTime(): Completable {
        return isGenuineTime()
            .flatMapCompletable {
                if (it) {
                    Completable.complete()
                } else {
                    Completable.error(NoGenuineTimeException())
                }
            }
    }

    open fun isGenuineTime(): Single<Boolean> {
        return getOrFetchOrRestoreServerTimeOffset()
            .map { abs(it) < MAXIMUM_SERVER_TIME_OFFSET }
            .onErrorReturnItem(false)
    }

    fun getIsGenuineTimeChanges(): Observable<Boolean> {
        return preferencesManager.getChanges(KEY_SERVER_TIME_OFFSET, Long::class.java)
            .flatMapSingle { isGenuineTime() }
            .distinctUntilChanged()
    }

    open fun getOrFetchOrRestoreServerTimeOffset(): Single<Long> {
        return Single.defer {
            if (timestampOffset != null) {
                Single.just(timestampOffset!!)
            } else {
                fetchServerTimeOffset()
            }
        }.onErrorResumeWith(restoreLastKnownServerTimeOffset())
            .onErrorResumeNext {
                Single.error(IllegalStateException("Unable to get server time offset", it))
            }
    }

    private fun restoreLastKnownServerTimeOffset(): Single<Long> {
        return preferencesManager.restore(KEY_SERVER_TIME_OFFSET, Long::class.java)
    }

    open fun fetchServerTime(): Single<Long> {
        return networkManager.lucaEndpointsV3
            .flatMap { it.serverTime }
            .map { it["unix"].asLong }
            .flatMap { TimeUtil.convertFromUnixTimestamp(it) }
    }

    private fun fetchServerTimeOffset(): Single<Long> {
        return fetchServerTime()
            .map { TimeUtil.getCurrentMillis() - it }
            .doOnSuccess {
                timestampOffset = it
                Timber.d("Server timestamp offset updated: %d", timestampOffset)
            }
            .doOnError { Timber.w("Unable to update server timestamp offset: %s", it.toString()) }
            .flatMap {
                preferencesManager.persist(KEY_SERVER_TIME_OFFSET, it).andThen(Single.just(it))
            }
    }

    override fun dispose() {
        super.dispose()
        timestampOffset = null
    }

    companion object {
        const val KEY_SERVER_TIME_OFFSET = "server_time_offset"
        val MAXIMUM_SERVER_TIME_OFFSET = TimeUnit.MINUTES.toMillis(5)
    }
}
