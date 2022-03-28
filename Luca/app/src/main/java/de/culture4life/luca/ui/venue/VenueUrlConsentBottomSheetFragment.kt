package de.culture4life.luca.ui.venue

import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.BottomSheetConsentVenueUrlBinding
import de.culture4life.luca.ui.base.BaseBottomSheetDialogFragment

class VenueUrlConsentBottomSheetFragment : BaseBottomSheetDialogFragment<VenueUrlConsentViewModel>() {

    private val url by lazy { arguments?.getString(ARGUMENT_URL_KEY) }
    private lateinit var binding: BottomSheetConsentVenueUrlBinding
    override var fixedHeight = true

    override fun getViewBinding(): ViewBinding {
        binding = BottomSheetConsentVenueUrlBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<VenueUrlConsentViewModel> = VenueUrlConsentViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        arguments?.apply {
            binding.consentInfoTextView.text =
                getString(R.string.venue_url_consent_description, getString(ARGUMENT_LOCATION_NAME_KEY), getString(ARGUMENT_URL_TYPE_KEY), url)
        }

        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked(url, binding.dontAskAgainSwitch.isChecked())
            dismiss()
        }
    }

    companion object {
        const val TAG = "VenueConsentLinkBottomSheetFragment"
        private const val ARGUMENT_LOCATION_NAME_KEY = "location_name"
        private const val ARGUMENT_URL_TYPE_KEY = "url_type"
        private const val ARGUMENT_URL_KEY = "url"

        fun newInstance(locationName: String?, readableUrlType: String?, url: String?): VenueUrlConsentBottomSheetFragment {
            return VenueUrlConsentBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARGUMENT_LOCATION_NAME_KEY to locationName,
                    ARGUMENT_URL_TYPE_KEY to readableUrlType,
                    ARGUMENT_URL_KEY to url
                )
            }
        }
    }
}
