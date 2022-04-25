package de.culture4life.luca.ui.lucaconnect

import android.app.Application
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import de.culture4life.luca.util.addTo
import de.culture4life.luca.whatisnew.WhatIsNewManager.Companion.ID_LUCA_CONNECT_MESSAGE
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LucaConnectBottomSheetViewModel(app: Application) : BaseFlowViewModel(app) {

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(updatePages())
            .andThen(updateEnrollmentSupportRecognized())
    }

    private fun updatePages(): Completable {
        return Completable.fromCallable {
            pages.apply {
                clear()
                add(LucaConnectFlowPage.ExplanationPage)
                add(LucaConnectFlowPage.ProvideProofPage)
                add(LucaConnectFlowPage.LucaConnectSharedDataPage)
                add(LucaConnectFlowPage.KritisPage)
                add(LucaConnectFlowPage.LucaConnectConsentPage)
                add(LucaConnectFlowPage.ConnectSuccessPage)
            }
        }
            .doOnComplete {
                updateAsSideEffect(onPagesUpdated, ViewEvent(pages))
            }
    }

    override fun onFinishFlow() {
        dismissBottomSheet()
    }

    fun onEnrollmentRequested() {
        application.connectManager.enroll()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { Timber.d("enrollment was successful") },
                { Timber.d(it, "enrollment failed") }
            )
    }

    private fun updateEnrollmentSupportRecognized(): Completable {
        return Completable.fromAction {
            val whatIsNewManager = application.whatIsNewManager
            whatIsNewManager.initialize(application)
                .andThen(whatIsNewManager.markMessageAsSeen(ID_LUCA_CONNECT_MESSAGE))
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(modelDisposable)
        }
    }
}
