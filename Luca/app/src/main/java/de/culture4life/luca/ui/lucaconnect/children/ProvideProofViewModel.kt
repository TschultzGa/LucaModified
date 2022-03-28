package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class ProvideProofViewModel(app: Application) : BaseFlowChildViewModel(app) {

    val validProofAvailable = MutableLiveData<ViewEvent<Boolean>>()

    override fun initialize(): Completable {
        return super.initialize()
            .doOnComplete {
                checkProofAvailable()
                    .subscribeOn(Schedulers.io())
                    .onErrorComplete()
                    .subscribe()
            }
    }

    private fun checkProofAvailable(): Completable {
        return application.connectManager.getLatestCovidCertificates()
            .subscribeOn(Schedulers.io())
            .toList()
            .flatMapCompletable { documents ->
                update(validProofAvailable, ViewEvent(!documents.isNullOrEmpty()))
            }
    }

    fun onCertificateAdded() {
        checkProofAvailable()
            .onErrorComplete()
            .subscribe()
            .addTo(modelDisposable)
    }

    fun onActionButtonClicked() {
        sharedViewModel?.navigateToNext()
    }
}
