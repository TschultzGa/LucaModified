package de.culture4life.luca.ui.common

import android.app.Application
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

/**
 * Detects barcodes using [BarcodeScanner] with [Barcode.FORMAT_QR_CODE].
 */
class LucaBarcodeScanner {

    private lateinit var scanner: BarcodeScanner

    private fun initializeIfRequired() {
        if (!::scanner.isInitialized) {
            scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
        }
    }

    fun detectBarcodes(application: Application, uri: Uri): Observable<String> {
        return Single.fromCallable { InputImage.fromFilePath(application, uri) }
            .flatMapObservable(::detectBarcodes)
    }

    fun detectBarcodes(image: InputImage): Observable<String> {
        return Completable.fromAction { initializeIfRequired() }
            .andThen(
                Observable.create<String> { emitter ->
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawBarcode = barcode.rawValue
                                if (rawBarcode != null) {
                                    emitter.onNext(rawBarcode)
                                } else {
                                    Timber.d("barcode detected but rawValue not available, perhaps not UTF-8 encoded")
                                }
                            }
                            emitter.onComplete()
                        }
                        .addOnFailureListener { emitter.tryOnError(it) }
                }
            )
            // Avoid to emit async events on MainThread which is rarely expected when chaining calls.
            .observeOn(Schedulers.io())
    }

    fun dispose() {
        if (::scanner.isInitialized) {
            scanner.close()
        }
    }
}
