package de.culture4life.luca.testtools.rules

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import de.culture4life.luca.R
import de.culture4life.luca.testtools.FixRobolectricIdlingResource
import java.lang.reflect.Field

/**
 * Defaults for testing with [FragmentScenario].
 *
 * - Provides convenience to put luca app Fragments under test.
 * - Ensure Fragments and ViewModels are released after each test method.
 */
class LucaFragmentScenarioRule<FRAGMENT : Fragment> @Deprecated("Use LucaFragmentScenarioRule.create() instead.") constructor(
    private val fragmentClass: Class<FRAGMENT>
) : BaseHookingTestRule() {

    private val lucaAppDefaultTheme = R.style.Theme_Luca_DayNight

    private var _scenario: FragmentScenario<FRAGMENT>? = null
    val scenario: FragmentScenario<FRAGMENT>
        get() = _scenario!!

    fun launch(bundle: Bundle? = null, initialState: Lifecycle.State = Lifecycle.State.RESUMED) {
        waitForIdle()
        _scenario = FragmentScenario.launchInContainer(fragmentClass, bundle, lucaAppDefaultTheme, initialState)
        waitForIdle()
    }

    fun launch(bundle: Bundle? = null, onCreated: (FRAGMENT) -> Unit = {}, onStarted: (FRAGMENT) -> Unit = {}) {
        launch(bundle, Lifecycle.State.CREATED)
        scenario.onFragment { onCreated(it) }
        scenario.moveToState(Lifecycle.State.STARTED)
        waitForIdle()
        scenario.onFragment { onStarted(it) }
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForIdle()
    }

    private fun waitForIdle() {
        FixRobolectricIdlingResource.waitForIdle()
    }

    /**
     * Launch the scenario but simulates navigation to fragment with some data after creation.
     *
     * The point here is that the Fragment is not RESUMED but already STARTED. After it becomes RESUMED
     * it should process the given arguments. This is similar to the behaviour of a ViewPager.
     */
    fun launchSimulateRedirect(bundleOf: Bundle, onCreated: () -> Unit = {}) {
        launch(initialState = Lifecycle.State.CREATED)
        onCreated()
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment {
            it.arguments = bundleOf
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        waitForIdle()
    }

    override fun beforeTest() {}

    override fun afterTest() {
        cleanUpScenario()
        releaseAndroidViewModels()
    }

    private fun cleanUpScenario() {
        _scenario?.moveToState(Lifecycle.State.DESTROYED)
        _scenario?.close()
    }

    private fun releaseAndroidViewModels() {
        // ViewModelFactory keeps static reference to ViewModels.
        // https://github.com/robolectric/robolectric/issues/6251
        // Next tests would try to reuse the previous ViewModel which will be instantiated with the previous LucaApplication.
        val instance: Field = ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("sInstance")
        instance.isAccessible = true
        instance.set(null, null)
    }

    companion object {
        inline fun <reified FRAGMENT : Fragment> create(): LucaFragmentScenarioRule<FRAGMENT> {
            @Suppress("DEPRECATION")
            return LucaFragmentScenarioRule(FRAGMENT::class.java)
        }
    }
}
