package de.culture4life.luca.ui.checkin.flow

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentVoluntaryCheckinBinding

class VoluntaryCheckInFragment : BaseCheckInFlowFragment<VoluntaryCheckInViewModel, CheckInFlowViewModel>() {

    private lateinit var binding: FragmentVoluntaryCheckinBinding

    override fun getViewModelClass(): Class<VoluntaryCheckInViewModel> = VoluntaryCheckInViewModel::class.java
    override fun getSharedViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentVoluntaryCheckinBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeActionButton()
    }

    private fun initializeActionButton() {
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked(binding.shareContactDataSwitch.isChecked(), binding.dontAskAgainCheckBox.isChecked)
        }
    }

    companion object {
        fun newInstance(): VoluntaryCheckInFragment = VoluntaryCheckInFragment()
    }
}