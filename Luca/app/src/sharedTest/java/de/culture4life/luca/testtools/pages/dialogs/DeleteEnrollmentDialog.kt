package de.culture4life.luca.testtools.pages.dialogs

import de.culture4life.luca.R

class DeleteEnrollmentDialog : DefaultOkContinueDialog() {
    override fun isDisplayed() {
        baseDialog.title.hasText(R.string.luca_id_deletion_dialog_title)
        baseDialog.message.hasText(R.string.luca_id_deletion_dialog_description)
    }
}
