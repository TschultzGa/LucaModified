package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.DocumentManager
import de.culture4life.luca.document.Documents
import de.culture4life.luca.registration.RegistrationData
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LucaConnectSharedDataViewModel(app: Application) : BaseFlowChildViewModel(app) {

    private val registrationManager = application.registrationManager

    private val registrationData = MutableLiveData<RegistrationData>()
    private val documents = MutableLiveData<List<Document>>()

    val additionalTransferData = MediatorLiveData<AdditionalTransferData>().apply {
        fun combine() {
            val registrationData = registrationData.value
            val documents = documents.value
            if (registrationData == null || documents.isNullOrEmpty()) {
                return
            }
            value = AdditionalTransferData(registrationData, documents[0].dateOfBirth)
        }

        addSource(registrationData) { combine() }
        addSource(documents) { combine() }
    }

    override fun initialize(): Completable {
        return super.initialize()
            .doOnComplete {
                Completable.mergeArrayDelayError(
                    initializeRegistrationData(),
                    initializeDocuments()
                )
                    .doOnError { Timber.w("Unable to initialize: $it") }
                    .onErrorComplete()
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            }
    }

    private fun initializeRegistrationData(): Completable {
        return registrationManager.getRegistrationData()
            .flatMapCompletable { update(registrationData, it) }
    }

    private fun initializeDocuments(): Completable {
        return application.connectManager.getLatestCovidCertificates()
            .toList()
            .flatMapCompletable { update(documents, it) }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepDocumentsUpdated()
        )
    }

    private fun keepDocumentsUpdated(): Completable {
        return preferencesManager.getChanges(DocumentManager.KEY_DOCUMENTS, Documents::class.java)
            .flatMapCompletable { initializeDocuments() }
    }

    fun onActionButtonClicked() {
        sharedViewModel?.navigateToNext()
    }

}