package de.culture4life.luca.ui.checkin

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.databinding.BottomSheetQrCodeBinding
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber


class QrCodeBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var sharedViewModel: QrCodeBottomSheetViewModel
    private lateinit var binding: BottomSheetQrCodeBinding
    private var qrCodeBitmap: Bitmap? = null
    private var isLoading = false
    private var isNetworkAvailable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetStyle)
        sharedViewModel = ViewModelProvider(requireActivity()).get(QrCodeBottomSheetViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetQrCodeBinding.inflate(inflater)

        initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
            .andThen(initializeViews())
            .subscribe(
                { Timber.d("Initialized %s with %s", this, sharedViewModel) },
                { Timber.e("Unable to initialize %s with %s: %s", this, sharedViewModel, it.toString()) }
            )

        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.attributes?.apply { screenBrightness = 1f }
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            with(BottomSheetBehavior.from(bottomSheet!!)) {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        sharedViewModel.onQrCodeBottomSheetClosed()
        super.onDismiss(dialog)
    }

    private fun initializeViewModel(): Completable {
        return Single.fromCallable { ViewModelProvider(requireActivity()).get(QrCodeBottomSheetViewModel::class.java) }
            .flatMapCompletable { it.initialize() }
    }

    private fun initializeViews(): Completable {
        return Completable.fromAction {
            qrCodeBitmap?.let {
                setQrCodeBitmap(it)
            }

            setIsLoading(isLoading)

            binding.includeEntryPolicyInfoImageView.setOnClickListener { showIncludeEntryPolicyInfoDialog() }
            binding.includeEntryPolicySwitch.setOnClickListener {
                if (binding.includeEntryPolicySwitch.isChecked) {
                    showIncludeEntryPolicyConsentDialog()
                } else {
                    sharedViewModel.onIncludeEntryPolicyToggled(false)
                }
            }
            sharedViewModel.includeEntryPolicy.observe(viewLifecycleOwner, { binding.includeEntryPolicySwitch.isChecked = it })

            sharedViewModel.onDocumentsUnavailable.observe(viewLifecycleOwner, { showDocumentsUnavailableError() })


            if (BuildConfig.DEBUG) {
                // simulate check in when clicking on the QR code in debug builds
                binding.qrCodeImageView.setOnClickListener { sharedViewModel.onDebuggingCheckInRequested() }
            }

            setNoNetworkWarningVisible(!isNetworkAvailable)
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
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.check_in_include_entry_policy_title)
            .setMessage(R.string.check_in_include_entry_policy_description)
            .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.cancel() }
        ).show()
    }

    private fun showIncludeEntryPolicyConsentDialog() {
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.check_in_include_entry_policy_consent_title)
            .setMessage(R.string.check_in_include_entry_policy_consent_description)
            .setPositiveButton(R.string.action_accept) { _, _ -> sharedViewModel.onIncludeEntryPolicyToggled(true) }
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                binding.includeEntryPolicySwitch.isChecked = false
                dialog.dismiss()
            }
        ).apply {
            isCancelable = false
            show()
        }
    }

    private fun showDocumentsUnavailableError() {
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
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

