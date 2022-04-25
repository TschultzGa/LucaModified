package de.culture4life.luca.ui.idnow

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentLucaIdVerificationBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.ClipboardUtil

class LucaIdVerificationFragment : BaseFragment<LucaIdVerificationViewModel>() {

    private lateinit var binding: FragmentLucaIdVerificationBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdVerificationBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass() = LucaIdVerificationViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.revocationCode) {
            binding.revocationCodeTextView.text = it
        }
        binding.copyCodeImageView.setOnClickListener {
            ClipboardUtil.copy(
                context = requireContext(),
                successText = getString(R.string.luca_id_revocation_code_copied),
                label = getString(R.string.luca_id_revocation_code_label),
                content = binding.revocationCodeTextView.text.toString()
            )
        }
    }
}
