package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class UserAuthenticationRequiredLucaIdIdentDialog : DefaultOkCancelDialog() {
    override fun isDisplayed() {
        baseDialog.message.containsText(context.getString(R.string.authentication_title))
        baseDialog.message.containsText(context.getString(R.string.authentication_luca_id_ident_subtitle))
        baseDialog.message.containsText(context.getString(R.string.authentication_luca_id_ident_description))
    }
}
