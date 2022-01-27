package de.culture4life.luca.ui.base

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent

abstract class BaseBottomSheetViewModel(app: Application) : BaseViewModel(app) {

    val onDismissBottomSheet: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val onViewDismissed: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    fun dismissBottomSheet() = updateAsSideEffect(onDismissBottomSheet, ViewEvent(true))
    fun viewDismissed() = updateAsSideEffect(onViewDismissed, ViewEvent(true))

}