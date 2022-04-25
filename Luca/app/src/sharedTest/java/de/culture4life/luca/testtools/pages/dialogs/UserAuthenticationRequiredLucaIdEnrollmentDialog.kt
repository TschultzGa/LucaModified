package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class UserAuthenticationRequiredLucaIdEnrollmentDialog : DefaultOkCancelDialog() {
    override fun isDisplayed() {
        baseDialog.message.containsText(context.getString(R.string.authentication_title))
        baseDialog.message.containsText(context.getString(R.string.authentication_luca_id_enrollment_subtitle))
        baseDialog.message.containsText(context.getString(R.string.authentication_luca_id_enrollment_description))
    }
}
