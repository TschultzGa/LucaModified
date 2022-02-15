package de.culture4life.luca.testtools

import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import de.culture4life.luca.LucaApplication
import io.github.kakaocup.kakao.Kakao
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Fix to wait until all resources are idle for Espresso together with Robolectric.
 *
 * See issue https://github.com/robolectric/robolectric/issues/4807
 *
 * How it works:
 * - Kakao allows to intercept each interaction (e.g. check/perform something)
 * - for every interaction we do
 * -- collect all registered IdlingResources
 * -- check whether all are idle
 * -- if not then wait few milliseconds and recheck for isIdle
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

    private fun waitForIdle() = runBlocking {
        val idlingRegistry = IdlingRegistry.getInstance()
        while (!idlingRegistry.resources.all(IdlingResource::isIdleNow)) {
            idlingRegistry.resources
                .filterNot(IdlingResource::isIdleNow)
                .forEach { idlingResource -> idlingResource.awaitUntilIdle() }
        }
    }

    private suspend fun IdlingResource.awaitUntilIdle() {
        // Using loop because some times, registerIdleTransitionCallback wasn't called.
        // https://github.com/robolectric/robolectric/issues/4807#issuecomment-813646235
        while (true) {
            if (isIdleNow) return else delay(10)
        }
    }
}