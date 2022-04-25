package de.culture4life.luca.testtools

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.R
import de.culture4life.luca.preference.EncryptedSharedPreferencesProvider
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions
import de.culture4life.luca.testtools.rules.*
import de.culture4life.luca.ui.consent.ConsentUiExtension
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
abstract class LucaFragmentTest<FRAGMENT : Fragment>(
    val fragmentScenarioRule: LucaFragmentScenarioRule<FRAGMENT>
) {
    val mockWebServerRule = MockWebServerRule()
    val mockServerPreconditions = MockServerPreconditions(mockWebServerRule)

    @get:Rule
    val ruleChain: RuleChain = RuleChain.emptyRuleChain() // Remember: first around() becomes first in @Before and last in @After.
        .around(MemoryUsageRule())
        .around(LoggingRule())
        .around(ReplaceRxJavaSchedulersRule.automaticExecution())
        .around(mockWebServerRule)
        .around(fragmentScenarioRule)

    val application: LucaApplication = ApplicationProvider.getApplicationContext()
    val testDisposable = CompositeDisposable()
    lateinit var testNavigationController: TestNavHostController

    @Before
    fun setupLucaFragmentTest() {
        // Clearing the app data should be done before each test instead after. That's because on
        // devices and emulators there is not guaranteed that the previous run has cleaned up all
        // data and states properly. That could happen when we just abort the run. The app data
        // becomes not cleared automatically. There is no new app for every test method/run.
        // For robolectric usually no issue because data is not shared between test method/run.
        if (LucaApplication.isRunningInstrumentationTests()) {
            clearSharedPreferences()

            // Usually it should be enough to clear the app storage to reset all states. But our Managers
            // has some inMemory Caching and it will not be reset between each instrumentationTest method.
            // The [Application.onTerminate] is called with robolectric only, not for instrumentationTest.
            application.invalidateAppState()
        }

        FixRobolectricIdlingResource.apply()
        Intents.init()
    }

    @After
    fun cleanupLucaFragmentTest() {
        // Attention! App state should be cleaned up before each test to ensure previous runs didn't
        // left stuff behind which would affect this run. See setup docs for more details.

        testDisposable.dispose()
        Intents.release()
    }

    /**
     * Mock [Navigation.findNavController] result and the following [androidx.navigation.NavController.navigate] calls.
     *
     * Has to be called after fragment instance is available and before onViewCreated.
     *
     * https://developer.android.com/guide/navigation/navigation-testing#test_navigationui_with_fragmentscenario
     */
    fun setupTestNavigationController(fragment: Fragment, @IdRes currentDestination: Int) {
        testNavigationController = TestNavHostController(application)
        testNavigationController.navigatorProvider = TestNavigatorProvider()
        fragment.viewLifecycleOwnerLiveData.observeForever { viewLifecycleOwner ->
            if (viewLifecycleOwner != null) {
                testNavigationController.setGraph(R.navigation.mobile_navigation)
                testNavigationController.setCurrentDestination(currentDestination)
                Navigation.setViewNavController(fragment.requireView(), testNavigationController)
            }
        }
    }

    private fun clearSharedPreferences() {
        // Usually it should work to call:
        //   LucaApplication.preferencesManager.deleteAll().blockingAwait()
        // But that leads after multiple runs to an issue:
        //   java.lang.SecurityException: Could not decrypt key. decryption failed
        //   Caused by: java.security.GeneralSecurityException: decryption failed
        EncryptedSharedPreferencesProvider(application).resetSharedPreferences(application).blockingAwait()
    }

    protected open fun <ManagerType : Manager> getInitializedManager(manager: ManagerType): ManagerType {
        manager.initialize(application).blockingAwait()
        return manager
    }

    fun waitForIdle() {
        FixRobolectricIdlingResource.waitForIdle()
    }

    /**
     * No one likes it but for fast feedback, we need a sleep as first step sometimes.
     *
     * Always try to find a different solution! e.g. IdlingResources
     *
     * At the moment we can't wait for rx delayed execution automatically.
     * https://github.com/square/RxIdler/issues/9
     */
    fun waitFor(milliseconds: Long) {
        // We have to add a few milliseconds to avoid race conditions when waiting for delays. It could
        // happen that we just finish shortly before delayed tasks is executed. Would mean that the app
        // state is still idle and next test step is performed before the delayed task becomes executed.
        FixRobolectricIdlingResource.waitForIdle(milliseconds + 10)
    }

    /**
     * Only by default available when we use our [de.culture4life.luca.ui.BaseActivity]. But for this
     * test type we try to isolate as much as possible to keep tests simple. For that Espresso enforce
     * us to use [androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity].
     */
    fun initializeConsentUiExtension() {
        fragmentScenarioRule.scenario.onFragment {
            ConsentUiExtension(it.childFragmentManager, application.consentManager, testDisposable)
        }
    }
}
