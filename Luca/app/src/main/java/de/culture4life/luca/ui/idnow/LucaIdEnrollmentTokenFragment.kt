package de.culture4life.luca.ui.idnow

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentLucaIdEnrollmentTokenBinding
import de.culture4life.luca.idnow.IdNowManager
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.ClipboardUtil

class LucaIdEnrollmentTokenFragment : BaseFragment<LucaIdEnrollmentTokenViewModel>() {

    private lateinit var binding: FragmentLucaIdEnrollmentTokenBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdEnrollmentTokenBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass() = LucaIdEnrollmentTokenViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.enrollmentToken) {
            binding.enrollmentTokenTextView.text = it
        }
        binding.copyTokenImageView.setOnClickListener {
            ClipboardUtil.copy(
                context = requireContext(),
                successText = getString(R.string.luca_id_enrollment_token_message_token_copied),
                label = getString(R.string.luca_id_enrollment_token_label),
                content = binding.enrollmentTokenTextView.text.toString()
            )
        }
        binding.actionButton.setOnClickListener {
            startActivity(IdNowManager.createIdNowIntent(requireContext(), viewModel.enrollmentToken.value ?: return@setOnClickListener))
        }
    }
}
