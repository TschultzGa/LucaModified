package de.culture4life.luca.testtools.rules

import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import de.culture4life.luca.R
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

    var scenario: FragmentScenario<FRAGMENT>? = null

    fun launchInContainer(bundle: Bundle? = null, @StyleRes theme: Int = lucaAppDefaultTheme) {
        scenario = FragmentScenario.launchInContainer(fragmentClass, bundle, theme)
    }

    override fun beforeTest() {}

    override fun afterTest() {
        cleanUpScenario()
        releaseAndroidViewModels()
    }

    private fun cleanUpScenario() {
        scenario?.moveToState(Lifecycle.State.DESTROYED)
        scenario?.close()
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