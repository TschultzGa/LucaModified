package de.culture4life.luca.ui.lucaconnect.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentConsentBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel


class ConsentFragment : BaseFlowChildFragment<ConsentViewModel, LucaConnectBottomSheetViewModel>() {

    private lateinit var binding: FragmentConsentBinding

    override fun getViewModelClass(): Class<ConsentViewModel> = ConsentViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentConsentBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        binding.consentCheckBox.setOnClickListener {
            binding.actionButton.isEnabled = binding.consentCheckBox.isChecked
        }
        binding.actionButton.isEnabled = binding.consentCheckBox.isChecked
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkIfNotificationsAreEnabled()
    }

    companion object {
        fun newInstance(): ConsentFragment {
            return ConsentFragment()
        }
    }
}