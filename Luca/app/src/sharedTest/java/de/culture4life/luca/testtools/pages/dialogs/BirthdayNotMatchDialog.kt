package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class BirthdayNotMatchDialog : DefaultOkDialog() {
    override fun isDisplayed() {
        baseDialog.title.hasText(R.string.document_import_error_different_birth_dates_title)
        baseDialog.message.hasText(R.string.document_import_error_different_birth_dates_description)
        okButton.hasText(R.string.action_ok)
    }
}
