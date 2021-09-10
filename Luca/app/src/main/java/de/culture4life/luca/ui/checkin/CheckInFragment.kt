package de.culture4life.luca.ui.checkin


import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.CheckBox
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentCheckInBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.BaseQrCodeViewModel.Companion.BARCODE_DATA_KEY
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.registration.RegistrationActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class CheckInFragment : BaseFragment<CheckInViewModel>() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isCheckinDialogVisible = false
    private lateinit var binding: FragmentCheckInBinding
    private var cameraPreviewDisposable: Disposable? = null
    private var bottomSheet: QrCodeBottomSheetFragment? = null
    private val listener = NavController.OnDestinationChangedListener { _, _, _ ->
        bottomSheet?.dismiss()
    }

    private lateinit var qrCodeBottomSheetViewModel: QrCodeBottomSheetViewModel

    override fun getViewBinding(): ViewBinding {
        binding = FragmentCheckInBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<CheckInViewModel> {
        return CheckInViewModel::class.java
    }

    override fun initializeViewModel(): Completable {
        return super.initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { viewModel.setupViewModelReference(requireActivity()) }
            .doOnComplete {
                qrCodeBottomSheetViewModel =
                    ViewModelProvider(requireActivity()).get(QrCodeBottomSheetViewModel::class.java)
            }
    }

    override fun initializeViews(): Completable {
        return super.initializeViews()
            .andThen(Completable.fromAction {
                initializeObservers()
                setOnClickListeners()
            })
    }

    private fun checkCameraPermission() {
        if (cameraPreviewDisposable != null) return
        val isCameraPermissionGranted = checkIfCameraPermissionWasGranted()
        if (isCameraPermissionGranted) showCameraPreview()
    }

    private fun initializeObservers() {
        observe(viewModel.qrCode) { bm ->
            bottomSheet?.setQrCodeBitmap(bm)
        }

        observe(viewModel.isNetworkAvailable) { value ->
            bottomSheet?.setNoNetworkWarningVisible(!value)
        }
        observe(viewModel.isLoading) { loading ->
            bottomSheet?.setIsLoading(loading)
        }
        observe(viewModel.isContactDataMissing) { contactDataMissing ->
            if (contactDataMissing) {
                showContactDataDialog()
            }
        }
        observe(viewModel.isUpdateRequired) { updateRequired ->
            if (updateRequired) {
                showUpdateDialog()
            }
        }
        observe(viewModel.privateMeetingUrl) { privateMeetingUrl ->
            if (privateMeetingUrl != null) {
                showJoinPrivateMeetingDialog(privateMeetingUrl)
            }
        }
        observe(viewModel.possibleDocumentData) { barcodeDataEvent ->
            if (!barcodeDataEvent.hasBeenHandled()) {
                hideCameraPreview()
                val bundle = Bundle()
                val barcodeData = barcodeDataEvent.valueAndMarkAsHandled
                bundle.putString(BaseQrCodeViewModel.BARCODE_DATA_KEY, barcodeData)
                BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.venue_check_in_document_redirect_title)
                    .setMessage(R.string.venue_check_in_document_redirect_description)
                    .setPositiveButton(R.string.action_continue) { _, _ ->
                        safeNavigateFromNavController(
                            R.id.myLucaFragment,
                            bundle
                        )
                    }
                    .setNegativeButton(R.string.action_cancel) { _, _ -> })
                    .apply {
                        onDismissListener =
                            DialogInterface.OnDismissListener {
                                showCameraPreview()
                            }
                    }
                    .show()
            }
        }
        observe(viewModel.confirmCheckIn) { viewEvent ->
            if (!viewEvent.hasBeenHandled()) {
                hideCameraPreview()
                val urlAndName = viewEvent.valueAndMarkAsHandled
                showConfirmCheckInDialog(urlAndName.first, urlAndName.second)
            }
        }
        observe(qrCodeBottomSheetViewModel.onBottomSheetClosed) {
            if (it.valueAndMarkAsHandled) {
                if (isAdded && checkIfCameraPermissionWasGranted()) setCameraPreviewVisible(true)
            }
        }
        observe(qrCodeBottomSheetViewModel.onDebuggingCheckInRequested) {
            if (it.valueAndMarkAsHandled) {
                if (BuildConfig.DEBUG) {
                    viewModel.onDebuggingCheckInRequested()
                }
            }
        }

        observe(viewModel.bundle) { bundle: Bundle? -> processBundle(bundle) }
    }

    private fun setOnClickListeners() {
        binding.showQrCodeButton.setOnClickListener { showQrCodeBottomSheet() }
        binding.createMeetingButton.setOnClickListener { showCreatePrivateMeetingDialog() }
        binding.requestCameraPermissionLinearLayout.setOnClickListener {
            showCameraPreview()
        }
        binding.flashLightButtonImageView.setOnClickListener { toggleFlashlight() }
        binding.historyTextView.setOnClickListener {
            safeNavigateFromNavController(R.id.action_checkInFragment_to_history)
        }
    }

    private fun toggleFlashlight() {
        val torchIsEnabled = camera?.cameraInfo?.torchState?.value == 1
        setTorchEnabled(!torchIsEnabled)
    }

    private fun setTorchEnabled(isEnabled: Boolean) {
        camera?.cameraControl?.enableTorch(isEnabled)
        binding.flashLightButtonImageView.setImageResource(if (isEnabled) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight_on)
    }

    private fun showCameraPreview() {
        cameraPreviewDisposable = cameraPermission
            .doOnComplete {
                setCameraPreviewVisible(true)
            }
            .andThen(startCameraPreview())
            .doOnError { throwable ->
                Timber.w("Unable to show camera preview: %s", throwable.toString())
                setCameraPreviewVisible(false)
            }
            .doFinally { hideCameraPreview() }
            .onErrorComplete()
            .subscribe()
        viewDisposable.add(cameraPreviewDisposable)
    }

    private fun showQrCodeBottomSheet() {
        setCameraPreviewVisible(false)
        parentFragmentManager.let {
            bottomSheet = QrCodeBottomSheetFragment.newInstance(
                qrCodeBitmap = viewModel.qrCode.value,
                isLoading = viewModel.isLoading.value,
                isNetworkAvailable = viewModel.isNetworkAvailable.value
            ).apply {
                show(it, tag)
            }
        }
    }

    private fun setCameraPreviewVisible(isVisible: Boolean) {
        binding.cameraContainerConstraintLayout.background = ContextCompat.getDrawable(
            requireContext(),
            if (isVisible) R.drawable.bg_camera_box_active_preview else R.drawable.bg_camera_box
        )
        binding.cameraPreviewView.isVisible = isVisible
        binding.requestCameraPermissionTextView.isVisible = !checkIfCameraPermissionWasGranted()
        binding.requestCameraPermissionLinearLayout.isVisible = !isVisible
    }

    override fun onResume() {
        super.onResume()
        hideKeyboard()
        viewModel.checkIfContactDataMissing()
        viewModel.checkIfUpdateIsRequired()
        viewModel.checkIfHostingMeeting()
        arguments?.let { bundle -> viewModel.setBundle(bundle) }
        if (!isCheckinDialogVisible) checkCameraPermission()
        navigationController.addOnDestinationChangedListener(listener)
    }

    override fun onStop() {
        viewModel.setBundle(null)
        super.onStop()
    }

    private fun processBundle(bundle: Bundle?) {
        if (bundle == null) {
            return
        }

        bundle.getString(BARCODE_DATA_KEY)?.let { barcode ->
            viewDisposable.add(
                viewModel.processBarcode(barcode)
                    .delaySubscription(500, TimeUnit.MILLISECONDS) // avoid processing if checked in
                    .onErrorComplete()
                    .subscribe()
            )
        }
    }

    private fun showContactDataDialog() {
        val dialogFragment = BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.registration_missing_info)
            .setMessage(R.string.registration_address_mandatory)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val intent = Intent(application, RegistrationActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            })
        dialogFragment.isCancelable = false
        dialogFragment.show()
    }

    private fun showUpdateDialog() {
        val dialogFragment = BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_required_title)
            .setMessage(R.string.update_required_description)
            .setPositiveButton(R.string.action_update) { _, _ ->
                try {
                    application.openUrl(
                        "market://details?id=" + BuildConfig.APPLICATION_ID.replace(
                            ".debug",
                            ""
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    application.openUrl("https://luca-app.de")
                }
            })
        dialogFragment.isCancelable = false
        dialogFragment.show()
        unbindCameraPreview()
    }

    private fun showJoinPrivateMeetingDialog(privateMeetingUrl: String) {
        val dialogFragment = BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.meeting_join_heading)
            .setMessage(R.string.meeting_join_description)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                viewModel.onPrivateMeetingJoinApproved(
                    privateMeetingUrl
                )
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                viewModel.onPrivateMeetingJoinDismissed(
                    privateMeetingUrl
                )
            })
        dialogFragment.isCancelable = false
        dialogFragment.show()
    }

    override fun onPause() {
        navigationController.removeOnDestinationChangedListener(listener)
        super.onPause()
    }

    private fun showCreatePrivateMeetingDialog() {
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.meeting_create_modal_heading)
            .setMessage(R.string.meeting_create_modal_description)
            .setPositiveButton(R.string.meeting_create_modal_action) { _, _ -> viewModel.onPrivateMeetingCreationRequested() }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.cancel() })
            .show()
    }

    fun startCameraPreview(): Completable {
        return Maybe.fromCallable { cameraProvider }
            .switchIfEmpty(Single.create { emitter ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(
                    requireContext()
                )
                cameraProviderFuture.addListener({
                    try {
                        cameraProvider = cameraProviderFuture.get()
                        emitter.onSuccess(cameraProvider)
                    } catch (e: ExecutionException) {
                        emitter.onError(e)
                    } catch (e: InterruptedException) {
                        emitter.onError(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            })
            .flatMapCompletable { cameraProvider ->
                Completable.create { emitter ->
                    cameraProvider?.let { bindCameraPreview(it) }
                    emitter.setCancellable { unbindCameraPreview() }
                }
            }
    }

    private fun bindCameraPreview(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(2048, 2048))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), viewModel)
        preview.setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
        camera = cameraProvider.bindToLifecycle(
            requireContext() as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
        binding.flashLightButtonImageView.isVisible = camera?.cameraInfo?.hasFlashUnit() == true
    }

    private fun unbindCameraPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        setTorchEnabled(false)
    }

    private fun hideCameraPreview() {
        cameraPreviewDisposable?.dispose()
        cameraPreviewDisposable = null
        setCameraPreviewVisible(false)
    }

    private fun showConfirmCheckInDialog(url: String, locationName: String) {
        isCheckinDialogVisible = true
        val dontAskView = View.inflate(
            ContextThemeWrapper(context, R.style.ThemeOverlay_Luca_AlertDialog),
            R.layout.layout_confirm_checkin_dont_ask,
            null
        )
        val checkBox = dontAskView.findViewById<CheckBox>(R.id.dontAskAgainCheckbox)
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.venue_check_in_confirmation_title)
            .setView(dontAskView)
            .setMessage(
                getString(
                    R.string.venue_check_in_confirmation_description,
                    locationName
                )
            )
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                if (viewModel.isCurrentDestinationId(R.id.checkInFragment)) {
                    showCameraPreview()
                }
                isCheckinDialogVisible = false

                dialog.cancel()
            }
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                binding.checkingInLoadingLayout.isVisible = true
                viewDisposable.add(
                    Completable.defer {
                        if (viewModel.isCurrentDestinationId(R.id.checkInFragment)) {
                            Completable.mergeArray(
                                viewModel.persistDontAskForConfirmation(checkBox.isChecked),
                                viewModel.handleSelfCheckInDeepLink(url)
                            )
                        } else {
                            Completable.complete()
                        }
                    }.onErrorComplete()
                        .doOnError { binding.checkingInLoadingLayout.isVisible = false }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
                )
            })
            .apply {
                isCancelable = false
                show()
            }
    }

}