package de.culture4life.luca.ui.qrcode

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import io.reactivex.rxjava3.core.Completable

class AddCertificateFlowViewModel(app: Application) : BaseFlowViewModel(app) {

    private val documentBarcodeProcessor = DocumentBarcodeProcessor(application, this)

    private var hasAddedDocument = false

    val addedDocument = MutableLiveData<ViewEvent<Boolean>>()
    val documentAddedOnViewDismissed = MutableLiveData<ViewEvent<Boolean>>()

    fun process(barcodeData: String): Completable {
        return documentBarcodeProcessor.process(barcodeData)
            .andThen(update(addedDocument, ViewEvent(true)))
            .doOnComplete { hasAddedDocument = true }
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
    }

    fun onAddCertificateViewDismissed() = updateAsSideEffect(documentAddedOnViewDismissed, ViewEvent(hasAddedDocument))

    override fun onFinishFlow() {
        dismissBottomSheet()
    }
}
