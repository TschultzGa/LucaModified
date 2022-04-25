package de.culture4life.luca.ui.idnow

import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.mocks.IntentMocks
import de.culture4life.luca.testtools.pages.LucaIdVerificationPage
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions.Companion.REVOCATION_CODE
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.whatisnew.WhatIsNewManager
import org.junit.Before
import org.junit.Test

class LucaIdVerificationFragmentTest : LucaFragmentTest<LucaIdVerificationFragment>(LucaFragmentScenarioRule.create()) {

    @Before
    fun setup() {
        with(mockServerPreconditions) {
            givenPowChallenge()
            givenLucaIdCreateEnrollment()
            givenLucaIdEnrollmentStatusSuccess()
            givenLucaIdDeleteIdent()
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
    fun showRecoveryToken() {
        IntentMocks.givenMarketIntentResponse()
        getInitializedManager(application.idNowManager)
            .initiateEnrollment().blockingAwait()
        getInitializedManager(application.idNowManager)
            .updateEnrollmentStatus().blockingAwait()
        fragmentScenarioRule.launch()

        LucaIdVerificationPage().run {
            revocationCode.hasText(REVOCATION_CODE)
        }
    }

    private fun assertMessageMarkedAsSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE)
            .test()
            .assertValue { it.seen }
    }

    private fun assertMessageMarkedAsNotSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE)
            .test()
            .assertValue { !it.seen }
    }
}
