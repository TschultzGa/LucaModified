package de.culture4life.luca.ui.checkin.flow.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentVoluntaryCheckinBinding
import de.culture4life.luca.ui.checkin.flow.BaseCheckInFlowFragment
import de.culture4life.luca.ui.checkin.flow.CheckInFlowViewModel

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
            viewModel.onActionButtonClicked(
                checkInAnonymously = !binding.shareContactDataSwitch.isChecked(),
                alwaysVoluntary = binding.dontAskAgainCheckBox.isChecked
            )
        }
    }

    companion object {
        fun newInstance(): VoluntaryCheckInFragment = VoluntaryCheckInFragment()
    }
}
