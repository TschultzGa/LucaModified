package de.culture4life.luca.ui.base.bottomsheetflow

import androidx.lifecycle.ViewModelStoreOwner
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.SharedViewModelScopeProvider

abstract class BaseFlowChildFragment<ChildViewModel : BaseFlowChildViewModel, ParentViewModel : BaseFlowViewModel> : BaseFragment<ChildViewModel>() {

    abstract fun getSharedViewModelClass(): Class<ParentViewModel>

    override fun initializeViews() {
        super.initializeViews()
        viewModel.setupSharedViewModelReference(getSharedViewModelStoreOwner(), getSharedViewModelClass())
    }

    /**
     * Gets the [ViewModelStoreOwner] for the shared ViewModels from the parent which has to be a [SharedViewModelScopeProvider].
     */
    private fun getSharedViewModelStoreOwner(): ViewModelStoreOwner = (parentFragment as SharedViewModelScopeProvider).sharedViewModelStoreOwner
}
