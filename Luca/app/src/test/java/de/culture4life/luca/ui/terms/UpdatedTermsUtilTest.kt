package de.culture4life.luca.ui.terms

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.ui.terms.UpdatedTermsUtil.Companion.areTermsAccepted
import de.culture4life.luca.ui.terms.UpdatedTermsUtil.Companion.markTermsAsAccepted
import org.junit.Test

class UpdatedTermsUtilTest : LucaUnitTest() {
    @Test
    fun areTermsAccepted_initially_isFalse() {
        areTermsAccepted(application)
            .test()
            .assertValue(false)
    }

    @Test
    fun areTermsAccepted_afterMarkingAccepted_isTrue() {
        markTermsAsAccepted(application)
            .andThen(areTermsAccepted(application))
            .test()
            .assertValue(true)
    }
}
