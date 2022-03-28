package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class DocumentImportConsentDialog : DefaultConsentDialog() {

    override fun isDisplayed() {
        title.hasText(R.string.consent_title)
        info.hasText(R.string.consent_import_document_description)
        acceptButton.hasText(R.string.document_import_action)
        acceptButton.isDisplayed()
        cancelButton.isNotDisplayed()
    }
}
