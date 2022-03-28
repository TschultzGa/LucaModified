package de.culture4life.luca.ui.base.bottomsheetflow

import de.culture4life.luca.ui.BaseFragment

abstract class BaseFlowChildFragment<ChildViewModel : BaseFlowChildViewModel, ParentViewModel : BaseFlowViewModel> : BaseFragment<ChildViewModel>() {

    abstract fun getSharedViewModelClass(): Class<ParentViewModel>

    override fun initializeViews() {
        super.initializeViews()
        viewModel.setupSharedViewModelReference(requireActivity(), getSharedViewModelClass())
    }
}
