package de.culture4life.luca.ui.qrcode.children

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseQrCodeCallback
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel
import io.reactivex.rxjava3.core.Completable

class ScanQrCodeViewModel(app: Application) : BaseFlowChildViewModel(app), BaseQrCodeCallback {

    val showCameraPreview = MutableLiveData<Boolean>()

    override fun processBarcode(barcodeData: String): Completable {
        return (sharedViewModel as AddCertificateFlowViewModel).process(barcodeData)
            .doOnSubscribe {
                updateAsSideEffect(showCameraPreview, false)
                updateAsSideEffect(isLoading, true)
            }
            .doFinally { updateAsSideEffect(isLoading, false) }
    }
}
