package de.culture4life.luca.ui.lucaconnect

import android.app.Application
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import de.culture4life.luca.ui.lucaconnect.children.*
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class LucaConnectBottomSheetViewModel(app: Application) : BaseFlowViewModel(app) {

    fun initializeViewModel(): Completable {
        return updatePages()
            .andThen(updateEnrollmentSupportRecognized())
    }

    private fun updatePages(): Completable {
        return Completable.fromCallable {
            pages.apply {
                clear()
                add(ExplanationFragment.newInstance())
                add(ProvideProofFragment.newInstance())
                add(LucaConnectSharedDataFragment.newInstance())
                add(ConsentFragment.newInstance())
                add(ConnectSuccessFragment.newInstance())
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
            application.connectManager.setEnrollmentSupportRecognized(true)
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(modelDisposable)
        }
    }
}