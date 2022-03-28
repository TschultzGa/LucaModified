package de.culture4life.luca.ui.checkin.flow

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.BottomSheetConsentEntryPolicyBinding
import de.culture4life.luca.ui.base.BaseBottomSheetDialogFragment

class ConsentEntryPolicySharingBottomSheetFragment : BaseBottomSheetDialogFragment<ConsentEntryPolicySharingViewModel>() {

    private lateinit var binding: BottomSheetConsentEntryPolicyBinding

    override fun getViewBinding(): ViewBinding {
        binding = BottomSheetConsentEntryPolicyBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<ConsentEntryPolicySharingViewModel> = ConsentEntryPolicySharingViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        initObservers()
    }

    private fun initObservers() {
        binding.actionButton.setOnClickListener {
            viewModel.onConsentAccepted(true)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            viewModel.onConsentAccepted(false)
            dismiss()
        }
    }

    companion object {
        const val TAG = "ConsentEntryPolicySharingBottomSheetFragment"

        fun newInstance(): ConsentEntryPolicySharingBottomSheetFragment {
            return ConsentEntryPolicySharingBottomSheetFragment()
        }
    }
}
