package de.culture4life.luca.ui.registration

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R

/** Helper to orchestrate multiple luca connect notices which the user has to accept. */
class LucaConnectNotice(val viewModel: RegistrationViewModel, val context: Context) {

    fun show(finalSuccessAction: Runnable) {
        when {
            viewModel.isNameChanged -> showNameChangeNotice(finalSuccessAction)
            viewModel.isPostalCodeChanged -> showPostalCodeChangeNotice(finalSuccessAction)
            viewModel.isAnyContactPropertyChanged -> showAnyContactPropertyChangeNotice(finalSuccessAction)
        }
    }

    private fun showPostalCodeChangeNotice(finalSuccessAction: Runnable) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.registration_update_postalCode_luca_connect_notice_title)
            .setMessage(R.string.registration_update_postalCode_luca_connect_notice_description)
            .setPositiveButton(R.string.registration_update_postalCode_luca_connect_notice_action) { _, _ -> finalSuccessAction.run() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showNameChangeNotice(finalSuccessAction: Runnable) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.registration_update_name_luca_connect_notice_title)
            .setMessage(R.string.registration_update_name_luca_connect_notice_description)
            .setPositiveButton(R.string.action_continue) { _, _ -> finalSuccessAction.run() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAnyContactPropertyChangeNotice(finalSuccessAction: Runnable) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.registration_update_any_luca_connect_notice_title)
            .setMessage(R.string.registration_update_any_luca_connect_notice_description)
            .setPositiveButton(R.string.action_continue) { _, _ -> finalSuccessAction.run() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
