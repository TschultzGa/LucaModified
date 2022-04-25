package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class UserAuthenticationNotActivatedDialog : DefaultOkContinueDialog() {
    override fun isDisplayed() {
        baseDialog.title.hasText(R.string.authentication_error_not_activated_title)
        baseDialog.message.hasText(R.string.authentication_error_not_activated_description)
    }
}
