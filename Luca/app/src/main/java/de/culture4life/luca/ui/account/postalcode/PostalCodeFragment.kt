package de.culture4life.luca.ui.account.postalcode

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentPostalCodeBinding
import de.culture4life.luca.ui.BaseFragment

class PostalCodeFragment : BaseFragment<PostalCodeViewModel>() {

    private lateinit var binding: FragmentPostalCodeBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentPostalCodeBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<PostalCodeViewModel> {
        return PostalCodeViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.postalCodeMatchingStatus) {
            binding.postalCodeToggle.isChecked = it
        }
        binding.postalCodeToggle.setOnClickListener { viewModel.onPostalCodeMatchingToggled(binding.postalCodeToggle.isChecked) }
    }

}