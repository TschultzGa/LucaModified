package de.culture4life.luca.ui.account.directcheckin

import de.culture4life.luca.databinding.FragmentDirectCheckInPreferencesBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.setCheckedImmediately

class DirectCheckInPreferencesFragment : BaseFragment<DirectCheckInPreferencesViewModel>() {
    private lateinit var binding: FragmentDirectCheckInPreferencesBinding

    override fun getViewBinding() = FragmentDirectCheckInPreferencesBinding.inflate(layoutInflater).also { binding = it }

    override fun getViewModelClass() = DirectCheckInPreferencesViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        binding.directCheckInToggle.setOnCheckedChangeListener { _, isChecked -> viewModel.onDirectCheckInToggled(isChecked) }
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.directCheckInToggle.setCheckedImmediately(viewModel.directCheckInStatus.value)
        observe(viewModel.directCheckInStatus) { binding.directCheckInToggle.isChecked = it }
    }
}
