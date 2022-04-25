package de.culture4life.luca.ui.idnow

import android.app.Application
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import io.reactivex.rxjava3.core.Completable

class IdNowEnrollFlowViewModel(context: Application) : BaseFlowViewModel(context) {

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(updatePages())
    }

    private fun updatePages(): Completable {
        return Completable.fromCallable {
            pages.apply {
                clear()
                add(LucaIdEnrollmentFlowPage.ExplanationPage)
                add(LucaIdEnrollmentFlowPage.ConsentPage)
                add(LucaIdEnrollmentFlowPage.SuccessPage)
            }
        }
            .doOnComplete {
                updateAsSideEffect(onPagesUpdated, ViewEvent(pages))
            }
    }

    override fun onFinishFlow() {
        dismissBottomSheet()
    }
}
