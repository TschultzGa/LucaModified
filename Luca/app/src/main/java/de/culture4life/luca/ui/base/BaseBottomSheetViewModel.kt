package de.culture4life.luca.ui.base

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent

abstract class BaseBottomSheetViewModel(app: Application) : BaseViewModel(app) {

    val dismissBottomSheetRequests: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val bottomSheetDismissed: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    fun dismissBottomSheet() = updateAsSideEffect(dismissBottomSheetRequests, ViewEvent(true))
    open fun onBottomSheetDismissed() = updateAsSideEffect(bottomSheetDismissed, ViewEvent(true))

}