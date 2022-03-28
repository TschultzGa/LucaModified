package de.culture4life.luca.testtools

import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import de.culture4life.luca.LucaApplication
import io.github.kakaocup.kakao.Kakao

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
}
