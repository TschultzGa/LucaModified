package de.culture4life.luca.ui.base.bottomsheetflow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.BaseBottomSheetViewModel

abstract class BaseFlowViewModel(app: Application) : BaseBottomSheetViewModel(app) {

    var pages = mutableListOf<BaseFlowPage>()

    val pagerNavigation: MutableLiveData<ViewEvent<PagerNavigate>> = MutableLiveData()
    val onPagesUpdated: MutableLiveData<ViewEvent<List<BaseFlowPage>>> = MutableLiveData()

    fun navigateToPrevious() = updateAsSideEffect(pagerNavigation, ViewEvent(PagerNavigate.PREVIOUS))
    fun navigateToNext() = updateAsSideEffect(pagerNavigation, ViewEvent(PagerNavigate.NEXT))

    abstract fun onFinishFlow()

    enum class PagerNavigate {
        PREVIOUS,
        NEXT
    }
}
