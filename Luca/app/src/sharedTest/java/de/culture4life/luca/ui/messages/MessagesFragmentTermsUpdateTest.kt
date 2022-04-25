package de.culture4life.luca.ui.messages

import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MessagesPage
import de.culture4life.luca.testtools.preconditions.ConsentPreconditions
import de.culture4life.luca.testtools.preconditions.WhatIsNewPreconditions
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.whatisnew.WhatIsNewManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class MessagesFragmentTermsUpdateTest : LucaFragmentTest<MessagesFragment>(LucaFragmentScenarioRule.create()) {

    private val consentPreconditions = ConsentPreconditions()

    @Before
    fun setup() {
        // hide messages not relevant for this test class
        WhatIsNewPreconditions().givenMessageEnabled(WhatIsNewManager.ID_POSTAL_CODE_MESSAGE, false)
    }

    @Test
    fun termsUpdateAvailable() {
        consentPreconditions.givenConsent(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID, false)
        launchFragment()

        MessagesPage().run {
            // terms update is shown
            messageList.childAt<MessagesPage.MessageItem>(0) { assertIsTermsUpdateMessage() }
            messageList.hasSize(1)

            // when accepted
            messageList.childAt<MessagesPage.MessageItem>(0) { click() }
            termsOfServiceUpdateDialog.acceptButton.click()

            // terms update is gone
            messageList.hasSize(0)
        }

        assertConsentGiven(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID, true)
    }

    @Test
    fun termsAccepted() {
        consentPreconditions.givenConsent(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID, true)
        launchFragment()
        MessagesPage().run {
            messageList.hasSize(0)
        }
    }

    private fun launchFragment() {
        // fix PreferencesManager has not been initialized yet when fragment is started in isolation
        getInitializedManager(application.preferencesManager)
        // fix sometimes WhatIsNewManager is not initialized yet when fragment is started in isolation
        getInitializedManager(application.whatIsNewManager)

        fragmentScenarioRule.launch()
        initializeConsentUiExtension()
    }

    private fun assertConsentGiven(consent: String, isGiven: Boolean) {
        val requiredConsent = application.consentManager.getConsent(consent).blockingGet()
        assertThat(requiredConsent.approved).isEqualTo(isGiven)
    }
}
