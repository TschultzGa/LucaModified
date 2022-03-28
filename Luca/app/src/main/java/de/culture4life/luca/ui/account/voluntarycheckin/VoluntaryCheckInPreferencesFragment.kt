package de.culture4life.luca.ui.account.voluntarycheckin

import androidx.core.view.isVisible
import de.culture4life.luca.databinding.FragmentVoluntaryCheckInPreferencesBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.setCheckedImmediately

class VoluntaryCheckInPreferencesFragment : BaseFragment<VoluntaryCheckInPreferencesViewModel>() {
    private lateinit var binding: FragmentVoluntaryCheckInPreferencesBinding

    override fun getViewModelClass() = VoluntaryCheckInPreferencesViewModel::class.java
    override fun getViewBinding() = FragmentVoluntaryCheckInPreferencesBinding.inflate(layoutInflater).also { binding = it }

    override fun initializeViews() {
        super.initializeViews()
        binding.voluntaryCheckInToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onVoluntaryCheckInToggled(isChecked)
            if (!isChecked) binding.voluntaryCheckInShareToggle.isChecked = false
            binding.voluntaryCheckInShareToggleContainer.isVisible = isChecked
            binding.voluntaryCheckInShareDescription.isVisible = isChecked
        }
        binding.voluntaryCheckInShareToggle.setOnCheckedChangeListener { _, isChecked -> viewModel.onVoluntaryCheckInShareToggled(isChecked) }
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.voluntaryCheckInToggle.setCheckedImmediately(viewModel.alwaysCheckInVoluntary.value)
        binding.voluntaryCheckInShareToggle.setCheckedImmediately(viewModel.alwaysCheckInAnonymously.value == false)
        observe(viewModel.alwaysCheckInVoluntary) { binding.voluntaryCheckInToggle.isChecked = it }
        observe(viewModel.alwaysCheckInAnonymously) { binding.voluntaryCheckInShareToggle.isChecked = !it }
    }
}
