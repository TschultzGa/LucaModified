package de.culture4life.luca.ui.base.bottomsheetflow

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import de.culture4life.luca.ui.BaseViewModel

abstract class BaseFlowChildViewModel(app: Application) : BaseViewModel(app) {
    protected var sharedViewModel: BaseFlowViewModel? = null

    fun <T : BaseFlowViewModel> setupSharedViewModelReference(activity: FragmentActivity, viewModelClass: Class<T>) {
        sharedViewModel = ViewModelProvider(activity).get(viewModelClass)
    }

    fun isLastPage(fragment: BaseFlowChildFragment<*, *>): Boolean = sharedViewModel?.isLastPage(fragment) == true
}
