package de.culture4life.luca.ui.qrcode.children

import android.app.Application
import android.net.Uri
import de.culture4life.luca.R
import de.culture4life.luca.document.DocumentParsingException
import de.culture4life.luca.ui.UserCancelledException
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class SelectInputViewModel(app: Application) : BaseQrCodeFlowChildViewModel(app) {

    override fun processBarcode(barcodeData: String): Completable {
        return (sharedViewModel as AddCertificateFlowViewModel).process(barcodeData)
    }

    fun importImage(uriSingle: Single<Uri>) {
        uriSingle
            .flatMapCompletable(::processImageFromContentUri)
            .doOnError {
                val errorBuilder = createErrorBuilder(it)
                    .withTitle(R.string.luca_connect_add_certificate_error_title)
                    .removeWhenShown()

                if (it is UserCancelledException) {
                    // user didn't select any image, no need to show an error
                    return@doOnError
                } else if (it is DocumentParsingException) {
                    // already handled in bas view model
                    return@doOnError
                } else if (it is NoSuchElementException) {
                    errorBuilder.withDescription(R.string.luca_connect_add_certificate_no_qr_code_error_description)
                        .isExpected
                }

                addError(errorBuilder.build())
            }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
            .addTo(modelDisposable)
    }

    fun onScanQrCodeSelected() {
        sharedViewModel?.navigateToNext()
    }

}