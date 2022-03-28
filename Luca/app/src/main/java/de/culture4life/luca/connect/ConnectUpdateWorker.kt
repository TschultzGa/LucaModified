package de.culture4life.luca.connect

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import de.culture4life.luca.LucaApplication
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class ConnectUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : RxWorker(appContext, workerParams) {

    override fun createWork(): Single<Result> {
        return Companion.createWork(applicationContext as LucaApplication)
            .andThen(Single.just(Result.success()))
            .onErrorReturnItem(Result.failure())
            .subscribeOn(Schedulers.io())
    }

    companion object {

        fun createWork(application: LucaApplication): Completable {
            with(application.connectManager) {
                return initialize(application)
                    .andThen(showEnrollmentSupportedNotificationIfRequired())
                    .andThen(updateMessagesIfRequired())
            }
        }
    }
}
