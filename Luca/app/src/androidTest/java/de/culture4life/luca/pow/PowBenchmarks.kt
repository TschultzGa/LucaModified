package de.culture4life.luca.pow

import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Ignore
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class PowBenchmarks : LucaInstrumentationTest() {

    private val challenge = PowChallenge(
        id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
        t = 722000,
        n = BigInteger(
            "144380763259650206165998492155351388825945452939423498473280949282313289136159375661015977582124727973311826782287838157897769664221001485570865841509663731386087436449391953482279620327058756416748340645252983772240122199269269171151377875385220439442022824366535997821105787938297760058083260498515463996677",
            10
        ),
        expirationTimestamp = TimeUtil.getCurrentMillis() + TimeUnit.MINUTES.toMillis(1)
    )

    @Ignore("Benchmark")
    @Test
    fun benchmark_fixedIterations() {
        val iterations = 10
        val startTime = TimeUtil.getCurrentMillis()
        for (i in 1..iterations) {
            challenge.calculateW()
        }
        val duration = TimeUtil.getCurrentMillis() - startTime
        val calculationsPerMinute = iterations * TimeUnit.MINUTES.toMillis(1) / duration.toFloat()
        val averageDuration = duration / iterations.toFloat()
        println("Calculations per minute: $calculationsPerMinute")
        println("Average calculation duration: $averageDuration milliseconds")
    }

    /**
     * Caution: may run endlessly on API 31 arm64-v8a emulators.
     */
    @Ignore("Benchmark")
    @Test
    fun benchmark_fixedDuration() {
        val duration = TimeUnit.SECONDS.toMillis(30)
        var count = 0
        Completable.fromAction {
            while (true) {
                challenge.calculateW()
                count++
            }
        }.timeout(duration, TimeUnit.MILLISECONDS)
            .onErrorComplete()
            .subscribeOn(Schedulers.computation())
            .test()
            .await()
        val calculationsPerMinute = count * TimeUnit.MINUTES.toMillis(1) / duration.toFloat()
        val averageDuration = duration / count.toFloat()
        println("Calculations per minute: $calculationsPerMinute")
        println("Average calculation duration: $averageDuration milliseconds")
    }

}