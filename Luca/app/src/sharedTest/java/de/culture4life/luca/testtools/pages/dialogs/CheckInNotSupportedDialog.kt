package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class CheckInNotSupportedDialog : DefaultOkDialog() {
    override fun isDisplayed() {
        baseDialog.title.hasText(R.string.document_import_error_check_in_scanner_title)
        baseDialog.message.hasText(R.string.document_import_error_check_in_scanner_description)
        okButton.hasText(R.string.action_ok)
    }
}
