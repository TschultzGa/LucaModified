package de.culture4life.luca.ui.myluca

import de.culture4life.luca.BuildConfig
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MyLucaPage
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import org.junit.Test

class MyLucaFragmentVersionCheckTest : LucaFragmentTest<MyLucaFragment>(LucaFragmentScenarioRule.create()) {

    @Test
    fun showUpdateDialogFromStatusCode() {
        mockServerPreconditions.givenHttpError(MockServerPreconditions.Route.SupportedVersion, NetworkManager.HTTP_UPGRADE_REQUIRED)
        givenRegisteredUser("Erika", "Mustermann")

        launchFragment()
        forceCheckUpdateRequired()

        MyLucaPage().run {
            updateDialog.isDisplayed()
        }
    }

    @Test
    fun showUpdateDialogFromMinimumVersion() {
        mockServerPreconditions.givenSupportedVersion(BuildConfig.VERSION_CODE + 1)
        givenRegisteredUser("Erika", "Mustermann")

        launchFragment()
        forceCheckUpdateRequired()

        MyLucaPage().run {
            updateDialog.isDisplayed()
        }
    }

    private fun launchFragment() {
        fragmentScenarioRule.launch()
    }

    private fun givenRegisteredUser(firstName: String, lastName: String) {
        // TODO make a reusable sample/precondition class after merge with id-enrollment

        // Transitive used PreferencesManager needs to be initialized before we can fake registered user.
        getInitializedManager(application.preferencesManager)

        val registrationManager = application.registrationManager
        val registrationData = registrationManager.getRegistrationData().blockingGet()

        registrationData.firstName = firstName
        registrationData.lastName = lastName

        registrationManager.persistRegistrationData(registrationData).blockingAwait()
    }

    private fun forceCheckUpdateRequired() {
        // TODO Try to make LucaApplication initialization configurable instead to force it, e.g. through a StubLucaApplication.

        // LucaApplication needs to have access to a FragmentManager to show dialogs.
        fragmentScenarioRule.scenario.onFragment { fragment ->
            application.onActivityStarted(fragment.requireActivity())
        }

        // Currently all app initializations steps are skipped for faster test execution. So we have to force it for now.
        application.checkUpdateRequired().blockingAwait()
    }
}
