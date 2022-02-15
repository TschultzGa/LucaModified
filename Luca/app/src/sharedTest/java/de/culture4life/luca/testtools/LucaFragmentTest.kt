package de.culture4life.luca.testtools

import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.preference.EncryptedSharedPreferencesProvider
import de.culture4life.luca.testtools.rules.*
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

    @get:Rule
    val ruleChain: RuleChain = RuleChain.emptyRuleChain() // Remember: first around() becomes first in @Before and last in @After.
        .around(MemoryUsageRule())
        .around(LoggingRule())
        .around(ReplaceRxJavaSchedulersRule.automaticExecution())
        .around(mockWebServerRule)
        .around(fragmentScenarioRule)

    val applicationContext = ApplicationProvider.getApplicationContext<LucaApplication>()

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
            applicationContext.invalidateAppState()
        }

        FixRobolectricIdlingResource.apply()
    }

    @After
    fun cleanupLucaFragmentTest() {
        // Attention! App state should be cleaned up before each test to ensure previous runs didn't
        // left stuff behind which would affect this run. See setup docs for more details.
    }

    private fun clearSharedPreferences() {
        // Usually it should work to call:
        //   LucaApplication.preferencesManager.deleteAll().blockingAwait()
        // But that leads after multiple runs to an issue:
        //   java.lang.SecurityException: Could not decrypt key. decryption failed
        //   Caused by: java.security.GeneralSecurityException: decryption failed
        EncryptedSharedPreferencesProvider(applicationContext).resetSharedPreferences(applicationContext).blockingAwait()
    }

    protected open fun <ManagerType : Manager> getInitializedManager(manager: ManagerType): ManagerType {
        manager.initialize(applicationContext).blockingAwait()
        return manager
    }
}