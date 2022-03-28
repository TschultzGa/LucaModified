package de.culture4life.luca.ui

import android.Manifest
import android.annotation.SuppressLint
import android.util.Size
import android.view.MotionEvent
import androidx.annotation.CallSuper
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.databinding.FragmentQrCodeScannerBinding
import de.culture4life.luca.ui.BaseQrCodeViewModel.CameraRequest.*
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BaseQrCodeFragment : BaseFragment<BaseQrCodeViewModel>() {

    protected var cameraProvider: ProcessCameraProvider? = null
    protected var camera: Camera? = null
    protected var cameraPreviewView: PreviewView? = null
    protected var cameraPreviewDisposable: Disposable? = null

    private lateinit var binding: FragmentQrCodeScannerBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentQrCodeScannerBinding.inflate(layoutInflater)
        cameraPreviewView = binding.cameraPreviewView
        return binding
    }

    override fun getViewModelClass() = BaseQrCodeViewModel::class.java

    fun setBarcodeResultCallback(callback: BaseQrCodeCallback) {
        viewModel.barcodeCallback = callback
    }

    /**
     * Does show or hide a loading animation on top of the camera preview.
     */
    fun showLoading(isLoading: Boolean) {
        viewModel.setIsLoading(isLoading)
    }

    @CallSuper
    override fun initializeViews() {
        super.initializeViews()
        initializeCameraPreview()
        observe(viewModel!!.isLoading) { isLoading ->
            binding.loadingOverlayLayout.isVisible = isLoading
        }
    }

    @CallSuper
    protected open fun initializeCameraPreview() {
        observe(viewModel!!.shouldShowCameraPreview()) {
            if (!it.hasBeenHandled()) {
                with(it.valueAndMarkAsHandled) {
                    if (showCamera) {
                        val requestMissingStuff = !onlyIfPossible
                        showCameraPreview(requestMissingStuff, requestMissingStuff)
                    } else {
                        hideCameraPreview()
                    }
                }
            }
        }

        cameraPreviewView = binding.cameraPreviewView
        binding.cameraContainerConstraintLayout.setOnClickListener {
            viewModel.setCameraPreviewRequest(ShowPreviewAndRequestMissingPermissions)
        }
        binding.flashLightButtonImageView.setOnClickListener { toggleTorch() }
    }

    /**
     * Will attempt to start the camera preview.
     *
     * Does start camera preview after checking that the prominent disclosure consent
     * has been given and the camera permission has been granted.
     *
     * Does not request the consent or permission.
     */
    fun requestShowCameraPreview() {
        viewModel.setCameraPreviewRequest(ShowPreviewOnlyIfPermissionsGiven)
    }

    /**
     * Will attempt to start the camera preview after checking that the prominent disclosure consent
     * has been given and the camera permission has been granted. Will request missing consent or
     * permission if enabled by the respective parameter or do nothing at all if not.
     */
    private fun showCameraPreview(requestConsent: Boolean, requestPermission: Boolean) {
        if (cameraPreviewDisposable != null) {
            Timber.d("Not starting camera preview, already started")
            return
        }
        val requestConsentIfRequired = viewModel!!.getCameraConsentGiven()
            .flatMapCompletable { isConsentGiven: Boolean ->
                if (!isConsentGiven) {
                    if (requestConsent) {
                        showCameraConsentDialog(false)
                        viewModel!!.onCameraConsentRequired()
                    }
                    return@flatMapCompletable Completable.error(IllegalStateException("Camera consent not given"))
                }
                return@flatMapCompletable Completable.complete()
            }

        val requestPermissionIfRequired = Single.fromCallable { isCameraPermissionGranted() }
            .flatMapCompletable { isPermissionGranted: Boolean ->
                if (!isPermissionGranted) {
                    if (requestPermission) {
                        viewModel!!.onCameraPermissionRequired()
                        return@flatMapCompletable requestCameraPermission()
                    }
                    return@flatMapCompletable Completable.error(IllegalStateException("Camera permission not granted"))
                }
                return@flatMapCompletable Completable.complete()
            }

        requestConsentIfRequired
            .andThen(requestPermissionIfRequired)
            .delay(50, TimeUnit.MILLISECONDS) // required because of a weird race condition with the camera preview view
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { setCameraPreviewVisible(true) }
            .andThen(startCameraPreview())
            .doOnError { Timber.w(it, "Unable to start camera preview: %s", it.toString()) }
            .onErrorComplete()
            .doFinally {
                setCameraPreviewVisible(false)
                cameraPreviewDisposable = null
            }
            .doOnSubscribe {
                Timber.d("Attempting to start camera preview")
                cameraPreviewDisposable = it
            }
            .subscribe()
            .addTo(viewDisposable)
    }

    fun requestHideCameraPreview() {
        viewModel.setCameraPreviewRequest(HidePreview)
    }

    private fun hideCameraPreview() {
        Timber.d("Hiding camera preview")
        cameraPreviewDisposable?.dispose()
        cameraPreviewDisposable = null
        unbindCameraPreview()
        setCameraPreviewVisible(false)
    }

    @CallSuper
    protected open fun setCameraPreviewVisible(isVisible: Boolean) {
        binding.cameraPreviewView.isVisible = isVisible
        binding.startCameraLinearLayout.isVisible = !isVisible
        binding.cameraContainerConstraintLayout.background = ContextCompat.getDrawable(
            requireContext(),
            if (isVisible) {
                R.drawable.bg_camera_box_active_preview
            } else {
                R.drawable.bg_camera_box
            }
        )
    }

    /**
     * Will attempt to get a camera provider and bind it to the lifecycle.
     * Will not complete. Will unbind the camera on disposal.
     *
     * Should only be used after the camera consent and permission have been given.
     */
    protected fun startCameraPreview(): Completable {
        return Maybe.fromCallable { cameraProvider }
            .switchIfEmpty(
                Single.create { emitter ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(
                        requireContext()
                    )
                    cameraProviderFuture.addListener({
                        try {
                            cameraProvider = cameraProviderFuture.get()
                            emitter.onSuccess(cameraProvider!!)
                        } catch (e: Exception) {
                            emitter.onError(e)
                        }
                    }, ContextCompat.getMainExecutor(requireContext()))
                    }
                )
                .flatMapCompletable { cameraProvider ->
                    Completable.create { emitter ->
                        cameraProvider?.let { bindCameraPreview(it) }
                        emitter.setCancellable { unbindCameraPreview() }
                    }
                }
        }

        @SuppressLint("ClickableViewAccessibility")
        @CallSuper
        protected open fun bindCameraPreview(cameraProvider: ProcessCameraProvider) {
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build()
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(IMAGE_ANALYSIS_RESOLUTION)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), viewModel!!)
            preview.setSurfaceProvider(cameraPreviewView!!.surfaceProvider)
            camera = cameraProvider.bindToLifecycle(
                requireContext() as LifecycleOwner,
                cameraSelector,
                imageAnalysis,
                preview
            )
            cameraPreviewView!!.setOnTouchListener { view, event ->
                return@setOnTouchListener when (event.action) {
                    MotionEvent.ACTION_DOWN -> true
                    MotionEvent.ACTION_UP -> {
                        val focusPoint = SurfaceOrientedMeteringPointFactory(
                            view.width.toFloat(), view.height.toFloat()
                        ).createPoint(event.x, event.y)
                        autoFocus(focusPoint)
                        true
                    }
                    else -> false
                }
            }

            binding.flashLightButtonImageView.isVisible = camera?.cameraInfo?.hasFlashUnit() == true
        }

        @CallSuper
        protected open fun unbindCameraPreview() {
            cameraProvider?.unbindAll()
            cameraProvider = null
            setTorchEnabled(false)
            binding.flashLightButtonImageView.isVisible = false
        }

        @SuppressLint("RestrictedApi")
        protected fun autoFocus(focusPoint: MeteringPoint) {
            try {
                Timber.d("Attempting to auto focus (%f, %f)", focusPoint.x, focusPoint.y)
                val autoFocusAction = FocusMeteringAction.Builder(
                    focusPoint,
                    FocusMeteringAction.FLAG_AF
                ).apply {
                    setAutoCancelDuration(3, TimeUnit.SECONDS)
                }.build()
                camera?.cameraControl?.startFocusAndMetering(autoFocusAction)
            } catch (e: Exception) {
                Timber.w("Unable to trigger auto-focus: %s", e.toString())
            }
        }

        protected fun toggleTorch() {
            val torchIsEnabled = camera?.cameraInfo?.torchState?.value == TorchState.ON
            setTorchEnabled(!torchIsEnabled)
        }

        @CallSuper
        open fun setTorchEnabled(isEnabled: Boolean) {
            camera?.cameraControl?.enableTorch(isEnabled)
            with(binding.flashLightButtonImageView) {
                if (isEnabled) {
                    setImageResource(R.drawable.ic_flashlight_off)
                    contentDescription = getString(R.string.check_in_scan_turn_off_flashlight_action)
                } else {
                    setImageResource(R.drawable.ic_flashlight_on)
                    contentDescription = getString(R.string.check_in_scan_turn_on_flashlight_action)
                }
            }
        }

        protected fun isCameraPermissionGranted(): Boolean {
            return rxPermissions.isGranted(Manifest.permission.CAMERA)
        }

        private fun requestCameraPermission(): Completable {
            return rxPermissions.request(Manifest.permission.CAMERA)
                .flatMapCompletable { granted: Boolean ->
                    if (granted) {
                        viewModel!!.onCameraPermissionGiven()
                        return@flatMapCompletable Completable.complete()
                    } else {
                        viewModel!!.onCameraPermissionDenied()
                        showCameraPermissionPermanentlyDeniedError()
                        return@flatMapCompletable Completable.error(IllegalStateException("Camera permission missing"))
                    }
                }
        }

        private fun showCameraConsentDialog(directToSettings: Boolean) {
            val consentManager = application.consentManager
            consentManager.initialize(application)
                .andThen(consentManager.requestConsentAndGetResult(ConsentManager.ID_ENABLE_CAMERA))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { consent, _ ->
                    if (consent.approved) {
                        if (directToSettings) {
                            application.openAppSettings()
                        } else {
                            viewModel!!.onCameraConsentGiven()
                        }
                    } else {
                        viewModel!!.onCameraConsentDenied()
                    }
                }
        }

        protected fun showCameraPermissionPermanentlyDeniedError() {
            showErrorAsSnackbar(
                ViewError.Builder(requireContext())
                    .withTitle(getString(R.string.missing_permission_arg, getString(R.string.permission_name_camera)))
                    .withDescription(getString(R.string.missing_permission_arg, getString(R.string.permission_name_camera)))
                    .withResolveLabel(getString(R.string.action_resolve))
                    .withResolveAction(Completable.fromAction { showCameraConsentDialog(true) })
                    .build()
            )
        }

        companion object {

            val IMAGE_ANALYSIS_RESOLUTION = Size(1920, 1080) // maximum resolution is 1080p
        }
    }
    