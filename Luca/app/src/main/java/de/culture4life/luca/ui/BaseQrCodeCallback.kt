package de.culture4life.luca.ui

import io.reactivex.rxjava3.core.Completable

interface BaseQrCodeCallback {
    fun processBarcode(barcodeData: String): Completable
}
