package de.culture4life.luca.testtools

import android.os.Bundle
import androidx.navigation.*
import androidx.navigation.fragment.FragmentNavigator

/**
 * Basically just a copy of [androidx.navigation.testing.TestNavigatorProvider].
 *
 * We expect the situation: fun createDestination() = [FragmentNavigator.Destination]
 * But the origin TestNavigatorProvider returns just a [NavDestination].
 * In our BaseFragment we use [FragmentNavigator.Destination] for easy "safe navigation" check.
 */
class TestNavigatorProvider : NavigatorProvider() {

    /**
     * A [Navigator] that only supports creating destinations.
     */
    @Navigator.Name("TestNavigator")
    class TestNavigator(val provider: NavigatorProvider) : Navigator<NavDestination>() {
        override fun createDestination() = FragmentNavigator.Destination(provider)

        override fun navigate(
            destination: NavDestination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Extras?
        ): NavDestination? {
            return destination
        }

        override fun popBackStack(): Boolean {
            return true
        }
    }

    private val navigator = TestNavigator(this)

    init {
        addNavigator(NavGraphNavigator(this))
    }

    override fun <T : Navigator<out NavDestination>> getNavigator(name: String): T {
        return try {
            super.getNavigator(name)
        } catch (e: IllegalStateException) {
            @Suppress("UNCHECKED_CAST")
            navigator as T
        }
    }
}
