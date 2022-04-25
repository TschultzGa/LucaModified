package de.culture4life.luca.ui.idnow

import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.whatisnew.WhatIsNewManager
import org.junit.Test

class LucaIdEnrollmentErrorFragmentTest : LucaFragmentTest<LucaIdEnrollmentErrorFragment>(LucaFragmentScenarioRule.create()) {

    @Test
    fun markAsSeen() {
        getInitializedManager(application.whatIsNewManager)

        assertMessageMarkedAsNotSeen()
        fragmentScenarioRule.launch()
        assertMessageMarkedAsSeen()
    }

    private fun assertMessageMarkedAsSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE)
            .test()
            .assertValue { it.seen }
    }

    private fun assertMessageMarkedAsNotSeen() {
        application.whatIsNewManager.getMessage(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE)
            .test()
            .assertValue { !it.seen }
    }
}
