package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class UpdateDialog : DefaultOkDialog() {
    override fun isDisplayed() {
        // TODO: add isCancelable=False logic if required
        baseDialog.title.hasText(R.string.update_required_title)
        baseDialog.message.hasText(R.string.update_required_description)
        okButton.hasText(R.string.action_update)
    }
}
