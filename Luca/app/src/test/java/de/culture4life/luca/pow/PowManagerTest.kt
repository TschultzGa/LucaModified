package de.culture4life.luca.pow

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler
import io.reactivex.rxjava3.internal.schedulers.IoScheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Test
import org.mockito.kotlin.*
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

class PowManagerTest : LucaUnitTest() {

    private val powManager = getInitializedManager(application.powManager)

    @Test
    fun solveChallenge_validChallenge_solvesChallenge() {
        val challenge = PowChallenge(
            id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
            t = 722000,
            n = BigInteger(
                "144380763259650206165998492155351388825945452939423498473280949282313289136159375661015977582124727973311826782287838157897769664221001485570865841509663731386087436449391953482279620327058756416748340645252983772240122199269269171151377875385220439442022824366535997821105787938297760058083260498515463996677",
                10
            ),
            expirationTimestamp = TimeUtil.getCurrentMillis() + TimeUnit.MINUTES.toMillis(1)
        )
        val expectedW = BigInteger(
            "52571132426657360441427045055983635933515940268316843025244762401389879291316542034234242948932966607766801473906759577822628298915837996423643809935627166826033619751573226601902604144301515523634718827548161237613335611612569397881443713947965167915036023670897926017830374588217369745916695296266512681931",
            10
        )
        powManager.solveChallenge(challenge)
            .andThen(Single.fromCallable { challenge.w })
            .test()
            .assertValue(expectedW)
    }

    @Test
    fun solveChallenge_multipleSubscriptions_handledCorrectly() {
        // We need asynchronous execution support to start simultaneous challenges.
        RxJavaPlugins.setIoSchedulerHandler { IoScheduler() }
        RxJavaPlugins.setComputationSchedulerHandler { ComputationScheduler() }

        val challengeControl = ControllableEndingChallenge()
        val challenge = challengeControl.challenge

        // start solving very hard challenge
        val firstSolve = powManager.solveChallenge(challenge)
            .subscribeOn(Schedulers.io())
            .test()

        // wait for a moment to ensure solving has been started (short delay until io thread is started)
        Thread.sleep(100)

        // solving has been started
        verify(challenge, times(1)).w

        // start solving the same challenge a second time, while the first time is still in progress
        val simultaneousSecondSolve = powManager.solveChallenge(challenge)
            .subscribeOn(Schedulers.io())
            .test()

        // wait for a moment to ensure solving has been started (short delay until io thread is started)
        Thread.sleep(100)

        firstSolve.assertNotComplete()
        simultaneousSecondSolve.assertNotComplete()

        // solving has been started only once
        verify(challenge, times(1)).w

        // simulate for the first solving to have an error (e.g. time out)
        firstSolve.onError(TimeoutException())
        firstSolve.await().assertError(TimeoutException::class.java)

        // disposed first solving doesn't affect second
        simultaneousSecondSolve.assertNoErrors()

        // simulate for the second solving an error (e.g. time out)
        simultaneousSecondSolve.onError(TimeoutException())
        simultaneousSecondSolve.await().assertError(TimeoutException::class.java)

        // TODO: 23.12.21 assert that actual solving has been stopped
        //  Bug? Solving still runs until done (unexpected?), but next steps show that we do not reconnect (expected!)

        // start solving the same challenge a third time, after previous solving has been disposed
        val separateThirdSolve = powManager.solveChallenge(challenge)
            .test()

        // disposed connected solving doesn't affect subsequent attempts
        separateThirdSolve.assertNoErrors()

        // solving has been started once again
        verify(challenge, times(1)).w

        // now we let the challenge succeed
        challengeControl.finishChallenge()
        separateThirdSolve.await().assertComplete()
    }

    @Test
    fun solveChallenge_invalidChallenge_emitsError() {
        val challenge = PowChallenge(
            id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
            t = -1,
            n = BigInteger.TEN,
            expirationTimestamp = TimeUtil.getCurrentMillis() + TimeUnit.MINUTES.toMillis(1)
        )
        powManager.solveChallenge(challenge)
            .test()
            .assertError(PowException::class.java)
    }

    @Test
    fun solveChallenge_expiredChallenge_emitsError() {
        val challenge = PowChallenge(
            id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
            t = 722000,
            n = BigInteger(
                "144380763259650206165998492155351388825945452939423498473280949282313289136159375661015977582124727973311826782287838157897769664221001485570865841509663731386087436449391953482279620327058756416748340645252983772240122199269269171151377875385220439442022824366535997821105787938297760058083260498515463996677",
                10
            ),
            expirationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.MINUTES.toMillis(1)
        )
        powManager.solveChallenge(challenge)
            .test()
            .assertError(PowException::class.java)
    }

    class ControllableEndingChallenge {

        private val reentrantLock = ReentrantLock()
        private val challengeResolveDelay = reentrantLock.newCondition()

        val challenge = spy(
            PowChallenge(
                id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
                t = 16777216,
                n = BigInteger(
                    "141034448973936786742004883589423835917046333314357166862744541233267794436471941128346182808342573051955753293376145261680565766448689199616637676739437441898555470277550267721520494868051272116099882854035081700058632932591595618511144338691068008156090713648385308498816464131219068207707532470678695509369",
                    10
                ),
                expirationTimestamp = TimeUtil.getCurrentMillis() + TimeUnit.MINUTES.toMillis(1)
            )
        ) {
            doAnswer {
                reentrantLock.lock()
                challengeResolveDelay.await()
                reentrantLock.unlock()
                BigInteger("123")
            }.whenever(it).w
        }

        fun finishChallenge() {
            reentrantLock.lock()
            challengeResolveDelay.signalAll()
            reentrantLock.unlock()
        }
    }
}
