package de.culture4life.luca.testtools.rules

import androidx.annotation.CallSuper
import androidx.test.espresso.IdlingRegistry
import com.squareup.rx3.idler.Rx3Idler
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.internal.schedulers.ComputationScheduler
import io.reactivex.rxjava3.internal.schedulers.IoScheduler
import io.reactivex.rxjava3.internal.schedulers.NewThreadScheduler
import io.reactivex.rxjava3.internal.schedulers.SingleScheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler

abstract class ReplaceRxJavaSchedulersRule : BaseHookingTestRule() {

    companion object {
        /**
         * Default RxJava schedulers behavior.
         *
         * Wraps our RxJava schedulers for Espresso [androidx.test.espresso.IdlingResource] feature.
         *
         * Attention: Don't use it for simple unit tests or there is a high risk for strange asynchronous effects and memory leaks.
         */
        fun automaticExecution() = IdlingSchedulersRule()

        /**
         * Replace our RxJava schedulers with controllable [TestScheduler].
         *
         * No [androidx.test.espresso.IdlingResource] feature active. Does not always work e.g click() event could trigger somewhere a blockingGet()
         * call and will then wait until executed. In this situation you have not so much options to call TestScheduler.triggerActions().
         *
         * Beware of the trap when working with [io.reactivex.rxjava3.subjects.Subject] e.g. [io.reactivex.rxjava3.subjects.BehaviorSubject].
         * https://georgimirchev.com/2020/08/12/race-conditions-in-unit-tests-with-rxjava-when-using-testscheduler/
         * - "subscriber to Subject ... doesn’t happen until triggerActions is invoked"
         */
        fun manualExecution() = TestSchedulersRule()

        /**
         * Replace our RxJava schedulers with [Schedulers.trampoline()].
         *
         * Mostly useful for debugging (e.g. does a delay somewhere stops my test execution to finish).
         */
        fun immediateExecution() = ImmediateSchedulersRule()
    }

    protected abstract val ioScheduler: Scheduler?
    protected abstract val computationScheduler: Scheduler?
    protected abstract val newThreadScheduler: Scheduler?
    protected abstract val singleScheduler: Scheduler?
    protected abstract val androidMainScheduler: Scheduler?

    @CallSuper
    override fun beforeTest() {
        // Calling [replaceSchedulers()] here is already too late and will result in flaky tests. That happen
        // because TestClass member initialization is already done before this method is called. And some test
        // do call [Manager.doInitialize()] for member initialization.
        // Do it in the [init { }] block at each sub class instead. In parent [init] the members aren't resolved.
    }

    @CallSuper
    override fun afterTest() {
        releaseRxSubscriptions()
    }

    protected fun replaceSchedulers() {
        // Should be done as early as possible, before any RX subscription happens (e.g an initialization() call) to get them all.
        ioScheduler?.let { scheduler -> RxJavaPlugins.setIoSchedulerHandler { scheduler } }
        computationScheduler?.let { scheduler -> RxJavaPlugins.setComputationSchedulerHandler { scheduler } }
        newThreadScheduler?.let { scheduler -> RxJavaPlugins.setNewThreadSchedulerHandler { scheduler } }
        singleScheduler?.let { scheduler -> RxJavaPlugins.setSingleSchedulerHandler { scheduler } }
        androidMainScheduler?.let { scheduler -> RxAndroidPlugins.setMainThreadSchedulerHandler { scheduler } }
    }

    private fun releaseRxSubscriptions() {
        // We still have leaks with Mockito + RxJava combinations e.g.
        //  spy(object).doSomething().subscribeOn(io).test().await()
        // Approach here is to try to cut active subscriptions/observations.
        ioScheduler?.shutdown()
        computationScheduler?.shutdown()
        newThreadScheduler?.shutdown()
        singleScheduler?.shutdown()
        androidMainScheduler?.shutdown()

        // use origin schedulers
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    class TestSchedulersRule : ReplaceRxJavaSchedulersRule() {

        val testScheduler = TestScheduler()

        override val ioScheduler = testScheduler
        override val computationScheduler = testScheduler
        override val newThreadScheduler = testScheduler
        override val singleScheduler = testScheduler
        override val androidMainScheduler = testScheduler

        init {
            replaceSchedulers()
        }
    }

    class IdlingSchedulersRule : ReplaceRxJavaSchedulersRule() {

        override val ioScheduler = Rx3Idler.wrap(IoScheduler(), "Io Scheduler")
        override val computationScheduler = Rx3Idler.wrap(ComputationScheduler(), "Computation Scheduler")
        override val newThreadScheduler = Rx3Idler.wrap(NewThreadScheduler(), "NewThread Scheduler")
        override val singleScheduler = Rx3Idler.wrap(SingleScheduler(), "Single Scheduler")
        override val androidMainScheduler: Scheduler? = null // Espresso already takes care about the main thread.

        init {
            replaceSchedulers()
        }

        override fun beforeTest() {
            super.beforeTest()
            registerIdlingResources()
        }

        override fun afterTest() {
            super.afterTest()
            unregisterIdlingResources()
        }

        private fun unregisterIdlingResources() {
            with(IdlingRegistry.getInstance()) {
                unregister(ioScheduler)
                unregister(computationScheduler)
                unregister(newThreadScheduler)
                unregister(singleScheduler)
            }
        }

        private fun registerIdlingResources() {
            with(IdlingRegistry.getInstance()) {
                register(ioScheduler)
                register(computationScheduler)
                register(newThreadScheduler)
                register(singleScheduler)
            }
        }
    }

    class ImmediateSchedulersRule : ReplaceRxJavaSchedulersRule() {

        override val ioScheduler: Scheduler = Schedulers.trampoline()
        override val computationScheduler: Scheduler = Schedulers.trampoline()
        override val newThreadScheduler: Scheduler = Schedulers.trampoline()
        override val singleScheduler: Scheduler = Schedulers.trampoline()
        override val androidMainScheduler: Scheduler? = null

        init {
            replaceSchedulers()
        }
    }
}
