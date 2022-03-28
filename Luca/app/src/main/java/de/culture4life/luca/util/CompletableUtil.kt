package de.culture4life.luca.util

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import java.util.concurrent.TimeUnit

fun Completable.retryWhenWithDelay(maximumRetries: Int, delayInSeconds: Long, scheduler: Scheduler, predicate: (Throwable) -> Boolean): Completable {
    return this.compose {
        it.retryWhen { errors ->
            errors.zipWith(Flowable.range(0, maximumRetries + 1)) { errors, attempts ->
                Pair(errors, attempts)
            }.flatMap { errorAndAttempt ->
                if (predicate(errorAndAttempt.first) && errorAndAttempt.second < maximumRetries) {
                    Flowable.just(errorAndAttempt.first).delay(delayInSeconds, TimeUnit.SECONDS, scheduler)
                } else {
                    Flowable.error(errorAndAttempt.first)
                }
            }
        }
    }
}
