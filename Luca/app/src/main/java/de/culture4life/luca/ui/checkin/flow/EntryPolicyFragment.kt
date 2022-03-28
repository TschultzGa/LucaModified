package de.culture4life.luca.ui.checkin.flow

import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentConfirmEntryPolicyStateBinding
import de.culture4life.luca.ui.dialog.BaseDialogFragment

class EntryPolicyFragment : BaseCheckInFlowFragment<EntryPolicyViewModel, CheckInFlowViewModel>() {

    private lateinit var binding: FragmentConfirmEntryPolicyStateBinding
    private val consentFragment = ConsentEntryPolicySharingBottomSheetFragment.newInstance()
    private val consentViewModel: ConsentEntryPolicySharingViewModel by activityViewModels()

    override fun getViewModelClass(): Class<EntryPolicyViewModel> = EntryPolicyViewModel::class.java
    override fun getSharedViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentConfirmEntryPolicyStateBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.entryPolicyDescriptionSwitch.setInfoTextOnClickListener { showInfoDialog() }

        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked(binding.entryPolicyDescriptionSwitch.isChecked(), binding.rememberSelectionDataSwitch.isChecked())
        }

        binding.entryPolicyDescriptionSwitch.setOnCheckedChangeListener { _, isChecked ->
            updatePersistSettingCheckBoxVisibility(isChecked)
            showConsentView()
        }

        observe(viewModel.onConsentValueChanged) {
            if (!it.hasBeenHandled()) {
                binding.entryPolicyDescriptionSwitch.setChecked(it.valueAndMarkAsHandled)
            }
        }

        observe(consentViewModel.onEntryPolicySharingConsentResult) {
            if (!it.hasBeenHandled()) {
                when (it.valueAndMarkAsHandled) {
                    true -> viewModel.onEntryPolicySharingConsented()
                    false -> viewModel.onEntryPolicySharingNotConsented()
                }
            }
        }
    }

    private fun updatePersistSettingCheckBoxVisibility(isChecked: Boolean) {
        binding.rememberSelectionDataSwitch.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
    }

    private fun showInfoDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.venue_check_in_share_entry_policy_title)
                .setMessage(R.string.venue_check_in_share_entry_policy_info_text)
                .setPositiveButton(R.string.action_ok) { _, _ -> }
        )
            .show()
    }

    private fun showConsentView() {
        if (binding.entryPolicyDescriptionSwitch.isChecked()) {
            consentFragment.show(parentFragmentManager, ConsentEntryPolicySharingBottomSheetFragment.TAG)
        }
    }

    companion object {
        fun newInstance(): EntryPolicyFragment = EntryPolicyFragment()
    }
}
