package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class DocumentImportConsentDialog : DefaultYesNoDialog() {

    override fun isDisplayed() {
        baseDialog.title.hasText(R.string.document_import_action)
        baseDialog.message.hasText(R.string.consent_import_document_description)
        okButton.hasText(R.string.action_ok)
        cancelButton.hasText(R.string.action_cancel)
    }

}