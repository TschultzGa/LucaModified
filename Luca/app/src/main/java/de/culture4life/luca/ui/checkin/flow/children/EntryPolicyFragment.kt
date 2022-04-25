package de.culture4life.luca.ui.checkin.flow.children

import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentConfirmEntryPolicyStateBinding
import de.culture4life.luca.ui.SharedViewModelScopeProvider
import de.culture4life.luca.ui.checkin.flow.BaseCheckInFlowFragment
import de.culture4life.luca.ui.checkin.flow.CheckInFlowViewModel
import de.culture4life.luca.ui.dialog.BaseDialogFragment

class EntryPolicyFragment : BaseCheckInFlowFragment<EntryPolicyViewModel, CheckInFlowViewModel>(), SharedViewModelScopeProvider {

    private lateinit var binding: FragmentConfirmEntryPolicyStateBinding
    private val consentFragment = ConsentEntryPolicySharingBottomSheetFragment.newInstance()
    private val consentViewModel by lazy { ViewModelProvider(sharedViewModelStoreOwner)[ConsentEntryPolicySharingViewModel::class.java] }

    override fun getViewModelClass(): Class<EntryPolicyViewModel> = EntryPolicyViewModel::class.java
    override fun getSharedViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java

    override val sharedViewModelStoreOwner: ViewModelStoreOwner
        get() = this

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
            if (it.isNotHandled) {
                binding.entryPolicyDescriptionSwitch.setChecked(it.valueAndMarkAsHandled)
            }
        }

        observe(consentViewModel.onEntryPolicySharingConsentResult) {
            if (it.isNotHandled) {
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
            consentFragment.show(childFragmentManager, ConsentEntryPolicySharingBottomSheetFragment.TAG)
        }
    }

    companion object {
        fun newInstance(): EntryPolicyFragment = EntryPolicyFragment()
    }
}
