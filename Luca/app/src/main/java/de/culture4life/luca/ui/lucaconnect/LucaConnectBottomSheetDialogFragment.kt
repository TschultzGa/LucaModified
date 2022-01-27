package de.culture4life.luca.ui.lucaconnect

import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment

class LucaConnectBottomSheetDialogFragment : BaseFlowBottomSheetDialogFragment<LucaConnectBottomSheetViewModel>() {

    override fun getViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = false

    override fun initializeViews() {
        super.initializeViews()
        viewModel.initializeViewModel().subscribe()
    }

    companion object {
        const val TAG = "LucaConnectBottomSheetDialogFragment"

        fun newInstance(): LucaConnectBottomSheetDialogFragment {
            return LucaConnectBottomSheetDialogFragment()
        }
    }
}