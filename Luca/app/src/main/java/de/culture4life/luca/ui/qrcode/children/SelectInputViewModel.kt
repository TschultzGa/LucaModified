package de.culture4life.luca.ui.qrcode.children

import android.app.Application
import android.net.Uri
import de.culture4life.luca.R
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.UserCancelledException
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber

class SelectInputViewModel(app: Application) : BaseFlowChildViewModel(app) {

    private val scanner = BaseQrCodeViewModel(app)

    private fun processBarcode(barcodeData: String): Completable {
        return (sharedViewModel as AddCertificateFlowViewModel).process(barcodeData)
    }

    fun importImage(uriSingle: Single<Uri>) {
        val importImageProcess = uriSingle
            .flatMapObservable(scanner::detectBarcodes)
            .firstOrError()
            .doOnError {
                val errorBuilder = createErrorBuilder(it)
                    .withTitle(R.string.luca_connect_add_certificate_error_title)
                    .removeWhenShown()

                when (it) {
                    is NoSuchElementException -> {
                        errorBuilder.withDescription(R.string.luca_connect_add_certificate_no_qr_code_error_description)
                        addError(errorBuilder.build())
                    }
                    is UserCancelledException -> {
                        // user didn't select any image, no need to show an error
                    }
                    else -> addError(errorBuilder.build())
                }
            }
            .flatMapCompletable(::processBarcode)
            .doOnError { Timber.w("Unable to process imported image: %s", it.toString()) }

        invoke(importImageProcess).subscribe()
    }

    fun onScanQrCodeSelected() {
        sharedViewModel?.navigateToNext()
    }
}
