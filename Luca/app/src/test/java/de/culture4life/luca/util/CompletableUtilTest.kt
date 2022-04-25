package de.culture4life.luca.util

import de.culture4life.luca.LucaUnitTest
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class CompletableUtilTest : LucaUnitTest() {

    private val testScheduler = TestScheduler(0, TimeUnit.SECONDS)
    private val mockList = mock(List::class.java)

    private val retryWhenWithDelayTest = Completable.fromCallable {
        mockList.size
    }.retryWhenWithDelay(MAX_RETRIES, MAX_DELAY_IN_SECONDS, testScheduler) {
        it is IllegalStateException
    }

    @Test
    fun `RxJava chain with retryWhenWithDelay is successful no retry is triggered`() {
        whenever(mockList.size).then { 0 }

        retryWhenWithDelayTest.test()

        val maxInvokeTime = MAX_RETRIES * MAX_DELAY_IN_SECONDS
        val maxInvocations = 1

        testScheduler.advanceTimeBy(maxInvokeTime, TimeUnit.SECONDS)
        verify(mockList, times(maxInvocations)).size

        testScheduler.advanceTimeBy(FAR_FUTURE_DELAY_IN_SECONDS, TimeUnit.SECONDS)
        verifyNoMoreInteractions(mockList)
    }

    @Test
    fun `retryWhenWithDelay will retry MAX_RETRIES when predicate is true`() {
        whenever(mockList.size).then { throw IllegalStateException() }

        retryWhenWithDelayTest.test()

        val maxInvokeTime = MAX_RETRIES * MAX_DELAY_IN_SECONDS
        val maxInvocations = MAX_RETRIES + 1

        testScheduler.advanceTimeBy(maxInvokeTime, TimeUnit.SECONDS)
        verify(mockList, times(maxInvocations)).size

        testScheduler.advanceTimeBy(FAR_FUTURE_DELAY_IN_SECONDS, TimeUnit.SECONDS)
        verifyNoMoreInteractions(mockList)
    }

    @Test
    fun `retryWhenWithDelay will not retry when predicate is false`() {
        whenever(mockList.size).then { throw RuntimeException() }

        retryWhenWithDelayTest.test()

        val maxInvokeTime = MAX_RETRIES * MAX_DELAY_IN_SECONDS
        val maxInvocations = 1

        testScheduler.advanceTimeBy(maxInvokeTime, TimeUnit.SECONDS)
        verify(mockList, times(maxInvocations)).size

        testScheduler.advanceTimeBy(FAR_FUTURE_DELAY_IN_SECONDS, TimeUnit.SECONDS)
        verifyNoMoreInteractions(mockList)
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val MAX_DELAY_IN_SECONDS = 30L
        private val FAR_FUTURE_DELAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(30)
    }
}
