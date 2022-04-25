package de.culture4life.luca.ui.checkin.flow.children

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentConfirmCheckInBinding
import de.culture4life.luca.ui.checkin.flow.BaseCheckInFlowFragment
import de.culture4life.luca.ui.checkin.flow.CheckInFlowViewModel

class ConfirmCheckInFragment : BaseCheckInFlowFragment<ConfirmCheckInViewModel, CheckInFlowViewModel>() {

    private lateinit var binding: FragmentConfirmCheckInBinding

    override fun getViewModelClass(): Class<ConfirmCheckInViewModel> = ConfirmCheckInViewModel::class.java
    override fun getSharedViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentConfirmCheckInBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeLocationName()
        initializeActionButton()
    }

    private fun initializeLocationName() {
        arguments?.apply {
            getString(KEY_LOCATION_NAME)?.let {
                binding.checkInDescriptionTextView.text = getString(R.string.venue_check_in_confirmation_description, it)
            }
        }
    }

    private fun initializeActionButton() {
        binding.actionButton.setOnClickListener { viewModel.onActionButtonClicked(binding.dontAskAgainSwitch.isChecked()) }
    }

    companion object {
        @VisibleForTesting
        const val KEY_LOCATION_NAME = ConfirmCheckInViewModel.KEY_LOCATION_NAME

        fun newInstance(arguments: Bundle): ConfirmCheckInFragment = ConfirmCheckInFragment().apply {
            this.arguments = arguments
        }

        fun createArguments(locationName: String?) = bundleOf(Pair(KEY_LOCATION_NAME, locationName))
    }
}
