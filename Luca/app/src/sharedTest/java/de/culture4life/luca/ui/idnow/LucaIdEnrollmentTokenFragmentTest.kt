package de.culture4life.luca.ui.idnow

import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import de.culture4life.luca.idnow.IdNowManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.mocks.IntentMocks.givenMarketIntentResponse
import de.culture4life.luca.testtools.pages.LucaIdEnrollmentTokenPage
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions.Companion.ENROLLMENT_TOKEN
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.whatisnew.WhatIsNewManager
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test

class LucaIdEnrollmentTokenFragmentTest : LucaFragmentTest<LucaIdEnrollmentTokenFragment>(LucaFragmentScenarioRule.create()) {

    @Before
    fun setup() {
        with(mockServerPreconditions) {
            givenPowChallenge()
            givenLucaIdCreateEnrollment()
            givenLucaIdEnrollmentStatusPending()
            givenAttestationNonce()
            givenAttestationRegister()
            givenAttestationAssert()
        }
    }

    @Test
    fun markAsSeen() {
        getInitializedManager(application.whatIsNewManager)
        assertMessageMarkedAsNotSeen()
        fragmentScenarioRule.launch()
        assertMessageMarkedAsSeen()
    }

    @Test
    fun openIdNowAppWhenAppNotInstalled() {
        givenMarketIntentResponse()
        getInitializedManager(application.idNowManager).initiateEnrollment().blockingAwait()
        fragmentScenarioRule.launch()

        LucaIdEnrollmentTokenPage().run {
            enrollmentToken.hasText(ENROLLMENT_TOKEN)
            actionButton.click()
            assertOpenPlayStoreAppIntent()
        }
    }

    private fun assertMessageMarkedAsSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE)
            .test()
            .assertValue { it.seen }
    }

    private fun assertMessageMarkedAsNotSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE)
            .test()
            .assertValue { !it.seen }
    }

    private fun assertOpenPlayStoreAppIntent() {
        Intents.intended(
            Matchers.allOf(
                IntentMatchers.hasAction(Intent.ACTION_VIEW),
                IntentMatchers.hasData(IdNowManager.ID_NOW_PLAY_STORE_URI)
            )
        )
    }
}
