package de.culture4life.luca.ui.account.entrypolicy

import de.culture4life.luca.databinding.FragmentEntryPolicyPreferencesBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.setCheckedImmediately

class EntryPolicyPreferencesFragment : BaseFragment<EntryPolicyPreferencesViewModel>() {
    private lateinit var binding: FragmentEntryPolicyPreferencesBinding

    override fun getViewBinding() = FragmentEntryPolicyPreferencesBinding.inflate(layoutInflater).also { binding = it }

    override fun getViewModelClass() = EntryPolicyPreferencesViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        binding.entryPolicyToggle.setOnCheckedChangeListener { _, isChecked -> viewModel.onEntryPolicyToggled(isChecked) }
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.entryPolicyToggle.setCheckedImmediately(viewModel.shareEntryPolicyAlways.value)
        observe(viewModel.shareEntryPolicyAlways) { binding.entryPolicyToggle.isChecked = it }
    }
}
