package de.culture4life.luca.ui

import android.annotation.SuppressLint
import android.app.Application
import android.media.Image
import androidx.annotation.CallSuper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.vision.common.InputImage
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.ui.BaseQrCodeViewModel.CameraRequest.HidePreview
import de.culture4life.luca.ui.BaseQrCodeViewModel.CameraRequest.ShowPreviewAndRequestMissingPermissions
import de.culture4life.luca.ui.common.LucaBarcodeScanner
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class BaseQrCodeViewModel(application: Application) :
    BaseViewModel(application), ImageAnalysis.Analyzer {

    protected var notificationManager: LucaNotificationManager = this.application.notificationManager

    private val scanner by lazy { LucaBarcodeScanner() }
    private var imageProcessingDisposable: Disposable? = null
    protected val showCameraPreview: MutableLiveData<ViewEvent<CameraRequest>> = MutableLiveData(ViewEvent(HidePreview))
    var pauseCameraImageProcessing = false
    var barcodeCallback: BaseQrCodeCallback? = null

    /*
        Camera consent
     */

    open fun getCameraConsentGiven(): Single<Boolean> {
        return preferencesManager.restoreOrDefault(KEY_CAMERA_CONSENT_GIVEN, false)
    }

    private fun setCameraConsentGiven(cameraConsentGiven: Boolean) {
        preferencesManager.persist(KEY_CAMERA_CONSENT_GIVEN, cameraConsentGiven)
            .andThen(Single.fromCallable { if (cameraConsentGiven) ShowPreviewAndRequestMissingPermissions else HidePreview })
            .flatMapCompletable { cameraRequest -> update(showCameraPreview, ViewEvent(cameraRequest)) }
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    open fun onCameraConsentRequired() {
    }

    open fun onCameraConsentGiven() {
        setCameraConsentGiven(true)
    }

    open fun onCameraConsentDenied() {
        setCameraConsentGiven(false)
    }

    /*
        Camera permission
     */

    open fun onCameraPermissionRequired() {
    }

    open fun onCameraPermissionGiven() {
        updateAsSideEffect(showCameraPreview, ViewEvent(ShowPreviewAndRequestMissingPermissions))
    }

    open fun onCameraPermissionDenied() {
        updateAsSideEffect(showCameraPreview, ViewEvent(HidePreview))
    }

    /*
        Camera image processing
     */

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!shouldProcessCameraImages()) {
            imageProxy.close()
            return
        }
        imageProcessingDisposable = processCameraImage(imageProxy)
            .doOnError { Timber.w("Unable to process camera image: %s", it.toString()) }
            .onErrorComplete()
            .doFinally { imageProxy.close() }
            .subscribeOn(Schedulers.computation())
            .subscribe()
        modelDisposable.add(imageProcessingDisposable)
    }

    /**
     * Indicates if new camera images should be processed. Should return false if an image
     * is currently processed or any UI is overlaying the camera preview.
     */
    @CallSuper
    protected open fun shouldProcessCameraImages(): Boolean {
        if (imageProcessingDisposable != null && !imageProcessingDisposable!!.isDisposed) {
            return false // still processing last image
        } else if (isLoading.value == true) {
            return false // something is still loading, e.g. a check-in request
        } else if (errors.value?.isNotEmpty() == true) {
            return false // an error is currently visible
        } else if (pauseCameraImageProcessing) {
            return false // processing paused
        }
        return true
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processCameraImage(imageProxy: ImageProxy): Completable {
        return Maybe.fromCallable<Image> { imageProxy.image }
            .map { image -> InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees) }
            .flatMapObservable(scanner::detectBarcodes)
            .flatMapCompletable { barcodeData -> processBarcode(barcodeData) }
    }

    private fun processBarcode(barcodeData: String): Completable {
        return application.notificationManager.vibrate()
            .andThen(barcodeCallback!!.processBarcode(barcodeData))
            .doOnSubscribe {
                // Pre set [isLoading] to avoid multiple simultaneous processing. The callback is too slow because of the complex chain and multiple
                //  thread switches when posting values to ViewModels. But the callback has to decide when done.
                //  CallbackViewModel -> LivaData -> CallbackFragment -> CameraFragment -> CameraViewModel -> LivaData -> CameraFragment
                updateAsSideEffect(isLoading, true)
            }
    }

    fun setIsLoading(isLoading: Boolean) {
        updateAsSideEffectIfRequired(this.isLoading, isLoading)
    }

    fun setCameraPreviewRequest(request: CameraRequest) {
        updateAsSideEffectIfRequired(showCameraPreview, ViewEvent(request))
    }

    fun shouldShowCameraPreview(): LiveData<ViewEvent<CameraRequest>> {
        return showCameraPreview
    }

    override fun onCleared() {
        super.onCleared()
        scanner.dispose()
    }

    sealed class CameraRequest(
        val showCamera: Boolean,
        val onlyIfPossible: Boolean = false
    ) {
        object ShowPreviewAndRequestMissingPermissions : CameraRequest(true, false)
        object ShowPreviewOnlyIfPermissionsGiven : CameraRequest(true, true)
        object HidePreview : CameraRequest(false, true)
    }

    companion object {
        const val BARCODE_DATA_KEY = "barcode_data"
    }
}
