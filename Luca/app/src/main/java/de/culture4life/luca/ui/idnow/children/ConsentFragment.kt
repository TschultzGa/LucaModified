package de.culture4life.luca.ui.idnow.children

import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaIdConsentBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.idnow.IdNowEnrollFlowViewModel
import de.culture4life.luca.ui.idnow.UserAuthenticationRequiredPrompt

class ConsentFragment : BaseFlowChildFragment<ConsentViewModel, IdNowEnrollFlowViewModel>() {

    private lateinit var binding: FragmentLucaIdConsentBinding
    private val userAuthenticationRequiredPrompt = UserAuthenticationRequiredPrompt(this)

    override fun getViewModelClass() = ConsentViewModel::class.java
    override fun getSharedViewModelClass() = IdNowEnrollFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdConsentBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.loadingIndicator.isVisible = it
            binding.consentDescriptionTextView.isVisible = !it
            binding.actionButton.isEnabled = !it
        }

        binding.actionButton.setOnClickListener {
            userAuthenticationRequiredPrompt.showForLucaIdEnrollment({ viewModel.onConsentGiven() })
        }
    }

    companion object {
        fun newInstance(): ConsentFragment {
            return ConsentFragment()
        }
    }
}
