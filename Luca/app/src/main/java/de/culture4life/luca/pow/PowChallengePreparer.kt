package de.culture4life.luca.pow

import de.culture4life.luca.network.pojo.PowSolutionRequestData
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

internal class PowChallengePreparer(
    private val challengeType: String,
    private val powManager: PowManager,
    private val managerDisposable: CompositeDisposable
) {

    private var powChallenge: PowChallenge? = null

    fun invokePowChallengeSolving(): Completable {
        return Completable.fromAction {
            getPowChallenge()
                .flatMapCompletable { powManager.solveChallenge(it) }
                .doOnSubscribe { Timber.d("Preparing proof of work for $challengeType") }
                .doOnComplete { Timber.d("Proof of work prepared for $challengeType") }
                .doOnError { Timber.w("Unable to prepare proof of work for $challengeType: $it") }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    private fun getPowChallenge(): Single<PowChallenge> {
        return Maybe.fromCallable<PowChallenge> { this.powChallenge }
            .switchIfEmpty(
                powManager.getChallenge(challengeType)
                    .doOnSuccess { this.powChallenge = it }
            )
    }

    fun createSolvedRequestData(): Single<PowSolutionRequestData> {
        return getPowChallenge()
            .flatMap(powManager::getSolvedChallenge)
            .map(::PowSolutionRequestData)
            .onErrorResumeNext {
                clearPowChallenge().andThen(Single.error(it))
            }
    }

    private fun clearPowChallenge(): Completable {
        return Completable.fromAction { this.powChallenge = null }
            .doOnComplete { Timber.d("Cleared proof of work for $challengeType") }
    }
}
