package de.culture4life.luca.testtools.rxjava

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.TestObserver
import org.assertj.core.api.Assertions.assertThat
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * Does observe the returned rx stream for subscriptions.
 *
 * Sample usage:
 *
 *    // call real method
 *    recorder = SubscriptionRecorder()
 *
 *    // or provide mocked response
 *    recorder = SubscriptionRecorder { Completable.complete() }
 *
 *    spy = spy(instance)
 *    whenever(spy.calledMethod()).doAnswer(recorder)
 *
 *    spyUser = Create(spy)
 *    spyUser.methodUseSpy()
 *
 *    recorder.verifySubscriptions(1)
 */
class SubscriptionRecorder(private val answer: (() -> Any)? = null) : Answer<Any> {

    private val observables = mutableListOf<TestObserver<*>>()

    override fun answer(invocation: InvocationOnMock): Any {
        val result = answer?.invoke() ?: invocation.callRealMethod()
        val observer = TestObserver.create<Any>()
        observables.add(observer)
        return when (result) {
            // Looks like always the same call, but they don't have a shared base.
            is Completable -> result.doOnSubscribe(observer::onSubscribe)
            is Single<*> -> result.doOnSubscribe(observer::onSubscribe)
            is Maybe<*> -> result.doOnSubscribe(observer::onSubscribe)
            is Observable<*> -> result.doOnSubscribe(observer::onSubscribe)
            else -> NotImplementedError("add case for ${result.javaClass.name}")
        }
    }

    fun verifySubscriptions(expectedCount: Int) {
        val subscriptions = observables.filter { it.hasSubscription() }
        assertThat(subscriptions.size).isEqualTo(expectedCount)
    }
}
