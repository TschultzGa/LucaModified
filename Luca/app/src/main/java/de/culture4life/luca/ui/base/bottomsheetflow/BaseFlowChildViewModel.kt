package de.culture4life.luca.ui.base.bottomsheetflow

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import de.culture4life.luca.ui.BaseViewModel

abstract class BaseFlowChildViewModel(app: Application) : BaseViewModel(app) {
    protected var sharedViewModel: BaseFlowViewModel? = null

    fun <T : BaseFlowViewModel> setupSharedViewModelReference(owner: ViewModelStoreOwner, viewModelClass: Class<T>) {
        sharedViewModel = ViewModelProvider(owner).get(viewModelClass)
    }
}
