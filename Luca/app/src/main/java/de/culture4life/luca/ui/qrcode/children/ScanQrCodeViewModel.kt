package de.culture4life.luca.ui.qrcode.children

import android.app.Application
import androidx.camera.core.ImageAnalysis
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel
import io.reactivex.rxjava3.core.Completable

class ScanQrCodeViewModel(app: Application) : BaseQrCodeFlowChildViewModel(app), ImageAnalysis.Analyzer {
    public override fun processBarcode(barcodeData: String): Completable {
        return (sharedViewModel as AddCertificateFlowViewModel).process(barcodeData)
            .doOnSubscribe {
                updateAsSideEffect(showCameraPreview, CameraRequest(false, true))
            }
    }
}