package de.culture4life.luca.testtools

import android.view.View
import androidx.test.espresso.*
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import de.culture4life.luca.LucaApplication
import io.github.kakaocup.kakao.Kakao
import org.hamcrest.Matcher
import org.hamcrest.Matchers

/**
 * Fix to wait until all resources are idle for Espresso together with Robolectric.
 *
 * See issue https://github.com/robolectric/robolectric/issues/4807
 *
 * How it works:
 * - Kakao allows to intercept each interaction (e.g. check/perform something)
 * - for every interaction we ensure that we wait until all IdlingResources are idle
 */
object FixRobolectricIdlingResource {

    fun apply() {
        if (LucaApplication.isRunningUnitTests()) {
            Kakao.intercept {
                onDataInteraction { onAll { waitForIdle() } }
                onViewInteraction { onAll { waitForIdle() } }
                onWebInteraction { onAll { waitForIdle() } }
            }
        }
    }

    fun waitForIdle() {
        val idlingRegistry = IdlingRegistry.getInstance()
        while (!idlingRegistry.resources.all(IdlingResource::isIdleNow)) {
            idlingRegistry.resources
                .filterNot(IdlingResource::isIdleNow)
                .forEach { idlingResource -> idlingResource.awaitUntilIdle() }
        }
        Espresso.onIdle()
    }

    fun waitForIdle(atLeast: Long) {
        // First let all current tasks finish to ensure delays are created.
        waitForIdle()
        // Then just loop the main thread all the delayed time.
        Espresso.onView(isRoot()).perform(LoopMainThreadFor(atLeast))
    }

    private fun IdlingResource.awaitUntilIdle() {
        while (true) {
            if (isIdleNow) {
                return
            } else {
                // If this approach is not enough then see following issue for more ideas.
                // https://github.com/robolectric/robolectric/issues/4807#issuecomment-813646235
                Espresso.onIdle()
            }
        }
    }

    class LoopMainThreadFor(private val delayInMillis: Long) : ViewAction {
        override fun getConstraints(): Matcher<View> = Matchers.isA(View::class.java)

        override fun getDescription(): String = "loop MainThread for $delayInMillis milliseconds"

        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(delayInMillis)

            // For robolctric [loopMainThreadForAtLeast] does return immediately.
            // So we have to delay by self.
            if (LucaApplication.isRunningUnitTests()) {
                val end = System.currentTimeMillis() + delayInMillis
                while (end > System.currentTimeMillis()) {
                    // Only short sleeps to give main thread the chance to perform its tasks.
                    Thread.sleep(10)
                    uiController.loopMainThreadUntilIdle()
                }
            }
        }
    }
}
