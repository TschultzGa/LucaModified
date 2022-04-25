package de.culture4life.luca.idnow

import android.content.Context
import androidx.work.*
import androidx.work.rxjava3.RxWorker
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

// TODO replace logic in ConnectManager if extension is accepted
class NetworkWorkerExt(
    workerTag: String,
    context: Context,
    disposable: CompositeDisposable
) {

    // Instead of switching the strategy here we shall use the test-worker.
    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing#concepts
    //
    // val testDriver = WorkManagerTestInitHelper.getTestDriver()
    // // Enqueue
    // workManager.enqueue(request).result.get()
    // // Tells the WorkManager test framework that initial delays are now met.
    // testDriver.setInitialDelayMet(request.id)
    private val delegate = if (LucaApplication.isRunningUnitTests() || LucaApplication.isRunningInstrumentationTests()) {
        RxIntervalStrategy(disposable)
    } else {
        WorkManagerStrategy(workerTag, context)
    }

    fun addWorker(initialDelay: Long, updateInterval: Long, worker: Class<out RxWorker>, work: Completable): Completable {
        return delegate.addWork(initialDelay, updateInterval, worker, work)
    }

    fun removeWorker() = Completable.fromAction {
        delegate.removeWorker()
    }

    interface Strategy {
        fun addWork(initialDelay: Long, updateInterval: Long, worker: Class<out RxWorker>, work: Completable): Completable
        fun removeWorker()
    }

    class WorkManagerStrategy(
        private val workerTag: String,
        context: Context
    ) : Strategy {

        private val workManager = WorkManager.getInstance(context)
        private val updateFlexPeriod = if (BuildConfig.DEBUG) PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS else TimeUnit.HOURS.toMillis(2)

        override fun addWork(initialDelay: Long, updateInterval: Long, worker: Class<out RxWorker>, work: Completable): Completable {
            return Completable.fromAction {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val updateWorkRequest: WorkRequest = PeriodicWorkRequest.Builder(
                    worker,
                    updateInterval, TimeUnit.MILLISECONDS,
                    updateFlexPeriod, TimeUnit.MILLISECONDS
                ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .addTag(workerTag)
                    .build()

                workManager.cancelAllWorkByTag(workerTag)
                workManager.enqueue(updateWorkRequest)
                Timber.d("Update work request submitted to work manager")
            }
        }

        override fun removeWorker() {
            workManager.cancelAllWorkByTag(workerTag)
        }
    }

    class RxIntervalStrategy(
        private val disposable: CompositeDisposable,
    ) : Strategy {

        private var interval: Disposable? = null

        override fun addWork(initialDelay: Long, updateInterval: Long, worker: Class<out RxWorker>, work: Completable): Completable {
            return Completable.fromAction {
                interval = Observable.interval(initialDelay, updateInterval, TimeUnit.MILLISECONDS, Schedulers.io())
                    .flatMapCompletable { work }
                    .doOnError { Timber.w("Unable to add work: $it") }
                    .onErrorComplete()
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                interval!!.addTo(disposable)
            }
        }

        override fun removeWorker() {
            interval?.dispose()
        }
    }
}
