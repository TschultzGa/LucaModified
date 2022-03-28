package de.culture4life.luca.ui.registration

import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.RegistrationPage
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import org.junit.Test
import java.net.HttpURLConnection

class RegistrationFragmentVersionCheckTest : LucaFragmentTest<RegistrationFragment>(LucaFragmentScenarioRule.create()) {

    @Test
    fun showUpdateDialogFromStatusCode() {
        // TODO improve mock response after merge with id-enrollment, there we have new test tools

        mockWebServerRule.mockResponse.apply {
            put("/api/v3/versions/apps/android") { setResponseCode(NetworkManager.HTTP_UPGRADE_REQUIRED) }
        }

        launchFragment()
        forceCheckUpdateRequired()

        RegistrationPage().run {
            updateDialog.isDisplayed()
        }
    }

    @Test
    fun showUpdateDialogFromMinimumVersion() {
        // TODO improve mock response after merge with id-enrollment, there we have new test tools

        mockWebServerRule.mockResponse.apply {
            put("/api/v3/versions/apps/android") {
                setResponseCode(HttpURLConnection.HTTP_OK)
                setBody("""{ "minimumVersion" = ${Int.MAX_VALUE} } """)
            }
        }

        launchFragment()
        forceCheckUpdateRequired()

        RegistrationPage().run {
            updateDialog.isDisplayed()
        }
    }

    private fun launchFragment() {
        fragmentScenarioRule.launch()
    }

    private fun forceCheckUpdateRequired() {
        // TODO Try to make LucaApplication initialization configurable instead to force it, e.g. through a StubLucaApplication.

        // LucaApplication needs to have access to a FragmentManager to show dialogs.
        fragmentScenarioRule.scenario.onFragment { fragment ->
            applicationContext.onActivityStarted(fragment.requireActivity())
        }

        // Currently all app initializations steps are skipped for faster test execution. So we have to force it for now.
        applicationContext.checkUpdateRequired().blockingAwait()
    }
}
