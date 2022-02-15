package de.culture4life.luca.ui.consent

import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.databinding.BottomSheetConsentBinding
import de.culture4life.luca.ui.base.BaseBottomSheetDialogFragment
import de.culture4life.luca.ui.consent.ConsentViewModel.Companion.KEY_CONSENT_ID
import timber.log.Timber

class ConsentBottomSheetFragment : BaseBottomSheetDialogFragment<ConsentViewModel>() {

    private lateinit var binding: BottomSheetConsentBinding

    override fun getViewBinding(): ViewBinding {
        binding = BottomSheetConsentBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<ConsentViewModel> = ConsentViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        binding.acceptButton.setOnClickListener { viewModel.onAcceptButtonClicked() }
        binding.cancelButton.setOnClickListener { viewModel.onCancelButtonClicked() }
        viewModel.consentId.observe(viewLifecycleOwner) {
            val texts = getConsentTexts(it)
            binding.consentHeaderTextView.text = texts.title
            binding.consentInfoTextView.text = texts.description
            binding.acceptButton.text = texts.action
        }
    }

    private fun getConsentTexts(consentId: String): ConsentTexts {
        val texts = ConsentTexts(
            title = getString(R.string.consent_title),
            description = getString(R.string.dummy_paragraph),
            action = getString(R.string.consent_accept_action)
        )
        when (consentId) {
            ConsentManager.ID_ENABLE_CAMERA -> texts.apply {
                title = getString(R.string.consent_enable_camera_title)
                description = getString(R.string.consent_enable_camera_description)
                action = getString(R.string.action_enable)
            }
            ConsentManager.ID_IMPORT_DOCUMENT -> texts.apply {
                description = getString(R.string.consent_import_document_description)
                action = getString(R.string.document_import_action)
            }
            ConsentManager.ID_INCLUDE_ENTRY_POLICY -> texts.apply {
                description = getString(R.string.consent_include_entry_policy_description)
            }
            ConsentManager.ID_POSTAL_CODE_MATCHING -> texts.apply {
                title = getString(R.string.consent_postal_code_matching_title)
                description = getString(R.string.consent_postal_code_matching_description)
            }
            else -> Timber.e("No texts configured for consent: $consentId")
        }
        return texts
    }

    private data class ConsentTexts(
        var title: String,
        var description: String,
        var action: String
    )

    companion object {

        fun newInstance(consentId: String): ConsentBottomSheetFragment {
            return ConsentBottomSheetFragment().apply {
                arguments = bundleOf(Pair(KEY_CONSENT_ID, consentId))
            }
        }
    }

}