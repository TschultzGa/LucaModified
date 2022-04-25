package de.culture4life.luca.ui.checkin

import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentCheckInBinding
import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.BaseQrCodeFragment
import de.culture4life.luca.ui.BaseQrCodeViewModel.Companion.BARCODE_DATA_KEY
import de.culture4life.luca.ui.SharedViewModelScopeProvider
import de.culture4life.luca.ui.checkin.flow.CheckInFlowBottomSheetFragment
import de.culture4life.luca.ui.checkin.flow.CheckInFlowViewModel
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.registration.RegistrationActivity
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import java.util.concurrent.TimeUnit

class CheckInFragment : BaseFragment<CheckInViewModel>(), NavController.OnDestinationChangedListener, SharedViewModelScopeProvider {

    private var qrCodeBottomSheet: QrCodeBottomSheetFragment? = null
    private val checkInFlowBottomSheet by lazy { CheckInFlowBottomSheetFragment.newInstance() }

    private lateinit var qrCodeBottomSheetViewModel: QrCodeBottomSheetViewModel
    private lateinit var checkInFlowViewModel: CheckInFlowViewModel

    private lateinit var binding: FragmentCheckInBinding
    private lateinit var cameraFragment: BaseQrCodeFragment

    override val sharedViewModelStoreOwner: ViewModelStoreOwner
        get() = this

    override fun getViewBinding(): ViewBinding {
        binding = FragmentCheckInBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<CheckInViewModel> {
        return CheckInViewModel::class.java
    }

    override fun getViewModelStoreOwner(): ViewModelStoreOwner {
        return requireActivity()
    }

    override fun initializeViewModel(): Completable {
        return super.initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                qrCodeBottomSheetViewModel = ViewModelProvider(sharedViewModelStoreOwner)
                    .get(QrCodeBottomSheetViewModel::class.java)
                checkInFlowViewModel = ViewModelProvider(sharedViewModelStoreOwner)
                    .get(CheckInFlowViewModel::class.java)
            }
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeCheckInButtons()
        initializeCameraPreview()
        initializeQrCodeBottomSheet()
        initializeCheckInMultiConfirmBottomSheet()
        initializeMiscellaneous()
    }

    private fun initializeCheckInButtons() {
        observe(viewModel.isDailyPublicKeyAvailable) { dailyKeyIsAvailable ->
            binding.showQrCodeButton.isVisible = dailyKeyIsAvailable
            binding.createMeetingButton.isVisible = dailyKeyIsAvailable
        }
        binding.showQrCodeButton.setOnClickListener { showQrCodeBottomSheet() }
        binding.createMeetingButton.setOnClickListener { showCreatePrivateMeetingDialog() }
    }

    private fun initializeCameraPreview() {
        cameraFragment = binding.qrCodeScanner.getFragment()
        cameraFragment.setBarcodeResultCallback(viewModel)
    }

    private fun initializeQrCodeBottomSheet() {
        observe(viewModel.qrCode) {
            qrCodeBottomSheet?.setQrCodeBitmap(it)
        }
        observe(qrCodeBottomSheetViewModel.bottomSheetDismissed) {
            if (it.isNotHandled) {
                it.isHandled = true
                if (isAdded) {
                    cameraFragment.requestShowCameraPreview()
                }
            }
        }
        observe(qrCodeBottomSheetViewModel.onDebuggingCheckInRequested) {
            if (it.isNotHandled) {
                it.isHandled = true
                if (BuildConfig.DEBUG) {
                    viewModel.onDebuggingCheckInRequested()
                }
            }
        }
    }

    private fun initializeCheckInMultiConfirmBottomSheet() {
        observe(viewModel.checkInMultiConfirm) {
            if (it.isNotHandled) {
                val urlAndLocationResponseData = it.valueAndMarkAsHandled
                showCheckInConfirmFlow(urlAndLocationResponseData.first, urlAndLocationResponseData.second)
            }
        }

        observe(checkInFlowViewModel.onCheckInRequested) {
            if (it.isNotHandled) {
                val checkInRequest = it.valueAndMarkAsHandled

                viewModel.onCheckInRequested(
                    checkInRequest.url,
                    checkInRequest.isAnonymous,
                    checkInRequest.shareEntryPolicyStatus
                )
            }
        }

        observe(checkInFlowViewModel.bottomSheetDismissed) {
            viewModel.onCheckInMultiConfirmDismissed()
        }
    }

    private fun initializeMiscellaneous() {
        observe(viewModel.bundleLiveData) { processBundle(it) }
        observe(viewModel.possibleDocumentData) {
            if (it.isNotHandled) {
                showImportDocumentDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.isLoading) {
            qrCodeBottomSheet?.setIsLoading(it)
            cameraFragment.showLoading(it)
        }
        observe(viewModel.isNetworkAvailable) {
            qrCodeBottomSheet?.setNoNetworkWarningVisible(!it)
        }
        observe(viewModel.isContactDataMissing) {
            if (it) {
                showContactDataMissingDialog()
            }
        }
        observe(viewModel.confirmPrivateMeeting) {
            if (it.isNotHandled) {
                showJoinPrivateMeetingDialog(it.valueAndMarkAsHandled)
            }
        }
        binding.historyActionBarMenuImageView.setOnClickListener {
            safeNavigateFromNavController(R.id.action_checkInFragment_to_history)
        }
        observe(viewModel.showCameraPreview) {
            if (it.isNotHandled) {
                if (it.value) {
                    cameraFragment.requestShowCameraPreview()
                } else {
                    cameraFragment.requestHideCameraPreview()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideKeyboard()
        viewModel.checkIfContactDataMissing()
        viewModel.checkIfHostingMeeting()
        arguments?.let { bundle -> viewModel.setBundle(bundle) }
        navigationController.addOnDestinationChangedListener(this)
    }

    override fun onPause() {
        navigationController.removeOnDestinationChangedListener(this)
        super.onPause()
    }

    override fun onStop() {
        clearBundle()
        super.onStop()
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        qrCodeBottomSheet?.dismiss()
    }

    private fun processBundle(bundle: Bundle?) {
        if (bundle == null) {
            return
        }
        bundle.getString(BARCODE_DATA_KEY)?.let { barcode ->
            viewModel.processBarcode(barcode)
                .delaySubscription(500, TimeUnit.MILLISECONDS) // avoid processing if checked in
                .doOnComplete { clearBundle() }
                .onErrorComplete()
                .subscribe()
                .addTo(viewDisposable)
        }
    }

    private fun showQrCodeBottomSheet() {
        cameraFragment.requestHideCameraPreview()
        childFragmentManager.let {
            qrCodeBottomSheet = QrCodeBottomSheetFragment.newInstance(
                qrCodeBitmap = viewModel.qrCode.value,
                isLoading = viewModel.isLoading.value,
                isNetworkAvailable = viewModel.isNetworkAvailable.value
            ).apply {
                show(it, tag)
            }
        }
    }

    private fun showCheckInConfirmFlow(url: String, locationResponseData: LocationResponseData) {
        cameraFragment.requestHideCameraPreview()

        checkInFlowBottomSheet.arguments = bundleOf(
            Pair(CheckInFlowBottomSheetFragment.KEY_LOCATION_URL, url),
            Pair(CheckInFlowBottomSheetFragment.KEY_LOCATION_RESPONSE_DATA, locationResponseData)
        )

        checkInFlowBottomSheet.show(childFragmentManager, CheckInFlowBottomSheetFragment.TAG)
    }

    private fun showJoinPrivateMeetingDialog(privateMeetingUrl: String) {
        cameraFragment.requestHideCameraPreview()
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.meeting_join_heading)
                .setMessage(R.string.meeting_join_description)
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    viewModel.onPrivateMeetingJoinApproved(privateMeetingUrl)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .apply {
                setOnDismissListener { viewModel.onPrivateMeetingJoinDismissed(privateMeetingUrl) }
                show()
            }
    }

    private fun showCreatePrivateMeetingDialog() {
        cameraFragment.requestHideCameraPreview()
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.meeting_create_modal_heading)
                .setMessage(R.string.meeting_create_modal_description)
                .setPositiveButton(R.string.meeting_create_modal_action) { _, _ ->
                    viewModel.onPrivateMeetingCreationRequested()
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .apply {
                setOnDismissListener { viewModel.onPrivateMeetingCreationDismissed() }
                show()
            }
    }

    private fun showImportDocumentDialog(documentData: String) {
        cameraFragment.requestHideCameraPreview()
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.venue_check_in_document_redirect_title)
                .setMessage(R.string.venue_check_in_document_redirect_description)
                .setPositiveButton(R.string.action_continue) { _, _ ->
                    val bundle = Bundle()
                    bundle.putString(BARCODE_DATA_KEY, documentData)
                    safeNavigateFromNavController(R.id.action_checkInFragment_to_myLucaFragment, bundle)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .apply {
                setOnDismissListener {
                    viewModel.onImportDocumentConfirmationDismissed()
                }
                show()
            }
    }

    private fun showContactDataMissingDialog() {
        cameraFragment.requestHideCameraPreview()
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.registration_missing_info)
                .setMessage(R.string.registration_address_mandatory)
                .setPositiveButton(R.string.action_ok) { _, _ ->
                    val intent = Intent(application, RegistrationActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    application.startActivity(intent)
                }
        )
            .apply {
                isCancelable = false
                setOnDismissListener {
                    viewModel.onContactDataMissingDialogDismissed()
                }
                show()
            }
    }

    private fun clearBundle() {
        arguments?.clear()
        viewModel.setBundle(null)
    }
}
