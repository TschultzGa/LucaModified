package de.culture4life.luca.pow

import android.content.Context
import de.culture4life.luca.Manager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.PowChallengeRequestData
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber

class PowManager(
    val networkManager: NetworkManager
) : Manager() {

    private val sharedChallengeObservables: MutableMap<PowChallenge, Observable<PowChallenge>> = HashMap()

    override fun doInitialize(context: Context): Completable {
        return networkManager.initialize(context)
    }

    /**
     * Fetches a new challenge from the backend but doesn't solve it.
     */
    fun getChallenge(type: String): Single<PowChallenge> {
        return networkManager.lucaEndpointsV4
            .flatMap { it.getPowChallenge(PowChallengeRequestData(type)) }
            .map { it.powChallenge }
            .onErrorResumeNext { Single.error(PowException("Unable to fetch challenge", it)) }
    }

    /**
     * Fetches a new challenge from the backend and solves it.
     */
    fun getSolvedChallenge(type: String): Single<PowChallenge> {
        return getChallenge(type)
            .flatMap { getSolvedChallenge(it) }
    }

    /**
     * Solves the specified challenge and emits the solved challenge.
     */
    fun getSolvedChallenge(challenge: PowChallenge): Single<PowChallenge> {
        return solveChallenge(challenge)
            .andThen(Single.just(challenge))
    }

    /**
     * Solves the specified challenge and completes.
     */
    fun solveChallenge(challenge: PowChallenge): Completable {
        return getSharedChallengeObservableIfAvailable(challenge)
            .doOnSuccess { Timber.v("Waiting for in-progress solving result for $challenge") }
            .switchIfEmpty(createSharedChallengeObservable(challenge))
            .flatMapObservable { it }
            .firstOrError()
            .ignoreElement()
    }

    /**
     * Solves the specified challenge and completes. If you want to avoid solving the
     * same challenge simultaneously, use [.solveChallenge] instead.
     */
    private fun doSolveChallenge(challenge: PowChallenge): Completable {
        return Single.fromCallable { challenge.w }
            .ignoreElement()
            .doOnSubscribe {
                checkExpirationTimestamp(challenge)
                Timber.d("Starting to solve challenge: $challenge")
            }
            .doOnComplete {
                Timber.v("Completed solving challenge: $challenge")
                checkExpirationTimestamp(challenge)
            }
            .doOnDispose { Timber.d("Stopping to solve challenge: $challenge") }
            .onErrorResumeNext { Completable.error(PowException("Unable to solve challenge", it)) }
    }

    private fun getSharedChallengeObservableIfAvailable(challenge: PowChallenge): Maybe<Observable<PowChallenge>> {
        return Maybe.fromCallable { sharedChallengeObservables[challenge] }
    }

    /**
     * Creates a connected observable and adds it to [.challengeObservables].
     * The first subscription will trigger a subscription to [.doSolveChallenge],
     * the last dispose will dispose the challenge solving.
     * Subscribe and dispose calls in between will have no effect on the challenge solving.
     */
    private fun createSharedChallengeObservable(challenge: PowChallenge): Single<Observable<PowChallenge>> {
        return Single.fromCallable {
            val observable = doSolveChallenge(challenge)
                .andThen(Single.just(challenge))
                .toObservable()
                .share()
            sharedChallengeObservables[challenge] = observable
            observable
        }.doOnSubscribe { Timber.v("Creating shared observable for challenge: $challenge") }
    }

    private fun checkExpirationTimestamp(challenge: PowChallenge) {
        require(challenge.expirationTimestamp > TimeUtil.getCurrentMillis()) { "Challenge expired" }
    }
}
