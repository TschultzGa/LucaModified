package de.culture4life.luca.ui.checkin

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.databinding.DialogQrCodeBinding
import de.culture4life.luca.ui.base.BaseBottomSheetDialogFragment
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

class QrCodeBottomSheetFragment : BaseBottomSheetDialogFragment<QrCodeBottomSheetViewModel>() {

    private lateinit var binding: DialogQrCodeBinding
    private var qrCodeBitmap: Bitmap? = null
    private var isLoading = false
    private var isNetworkAvailable = true

    override fun getViewBinding(): ViewBinding {
        binding = DialogQrCodeBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<QrCodeBottomSheetViewModel> = QrCodeBottomSheetViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        qrCodeBitmap?.let {
            setQrCodeBitmap(it)
        }

        setIsLoading(isLoading)

        binding.includeEntryPolicyInfoImageView.setOnClickListener { showIncludeEntryPolicyInfoDialog() }
        binding.includeEntryPolicySwitch.setOnClickListener {
            if (binding.includeEntryPolicySwitch.isChecked) {
                showIncludeEntryPolicyConsentDialog()
            } else {
                viewModel.onIncludeEntryPolicyToggled(false)
            }
        }
        setNoNetworkWarningVisible(!isNetworkAvailable)
        initializeObservers()
    }

    private fun initializeObservers() {
        viewModel.includeEntryPolicy.observe(viewLifecycleOwner) { binding.includeEntryPolicySwitch.isChecked = it }
        viewModel.onDocumentsUnavailable.observe(viewLifecycleOwner) {
            if (it.isNotHandled) {
                it.isHandled = true
                showDocumentsUnavailableError()
            }
        }
        viewModel.onOnlyInvalidDocumentsAvailable.observe(viewLifecycleOwner) {
            if (it.isNotHandled) {
                it.isHandled = true
                showDocumentsInvalidError()
            }
        }
        if (BuildConfig.DEBUG) {
            // simulate check in when clicking on the QR code in debug builds
            binding.qrCodeImageView.setOnClickListener { viewModel.onDebuggingCheckInRequested() }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        return dialog.apply {
            window?.attributes?.screenBrightness = 1f
        }
    }

    fun setQrCodeBitmap(bitmap: Bitmap) {
        qrCodeBitmap = bitmap
        if (!::binding.isInitialized) return
        binding.qrCodeImageView.setImageBitmap(bitmap)
        setIsLoading(false)
    }

    fun setIsLoading(isLoading: Boolean) {
        val showLoadingIndicator = isLoading || qrCodeBitmap == null
        binding.loadingLayout.isVisible = showLoadingIndicator
        binding.qrCodeImageView.isVisible = !showLoadingIndicator
    }

    fun setNoNetworkWarningVisible(isWarningVisible: Boolean) {
        if (!::binding.isInitialized) return
        binding.myQrCodeDescriptionTextView.isVisible = !isWarningVisible
        binding.noNetworkWarningTextView.isVisible = isWarningVisible
    }

    private fun showIncludeEntryPolicyInfoDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.check_in_include_entry_policy_title)
                .setMessage(R.string.check_in_include_entry_policy_description)
                .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.cancel() }
        ).show()
    }

    private fun showIncludeEntryPolicyConsentDialog() {
        val application = requireActivity().application as LucaApplication
        val consentManager = application.consentManager
        consentManager.initialize(application)
            .andThen(consentManager.requestConsentAndGetResult(ConsentManager.ID_INCLUDE_ENTRY_POLICY))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { consent, _ ->
                if (consent.approved) {
                    viewModel.onIncludeEntryPolicyToggled(true)
                } else {
                    binding.includeEntryPolicySwitch.isChecked = false
                }
            }
    }

    private fun showDocumentsInvalidError() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.check_in_documents_invalid_title)
                .setMessage(R.string.check_in_documents_invalid_description)
                .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.cancel() }
        ).apply {
            setOnDismissListener { binding.includeEntryPolicySwitch.isChecked = false }
            show()
        }
    }

    private fun showDocumentsUnavailableError() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.check_in_documents_unavailable_title)
                .setMessage(R.string.check_in_documents_unavailable_description)
                .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.cancel() }
        ).apply {
            setOnDismissListener { binding.includeEntryPolicySwitch.isChecked = false }
            show()
        }
    }

    companion object {
        fun newInstance(
            qrCodeBitmap: Bitmap?,
            isLoading: Boolean?,
            isNetworkAvailable: Boolean?
        ): QrCodeBottomSheetFragment {
            return QrCodeBottomSheetFragment().apply {
                qrCodeBitmap?.let { this.qrCodeBitmap = it }
                isLoading?.let { this.isLoading = it }
                isNetworkAvailable?.let { this.isNetworkAvailable = it }
            }
        }
    }
}
