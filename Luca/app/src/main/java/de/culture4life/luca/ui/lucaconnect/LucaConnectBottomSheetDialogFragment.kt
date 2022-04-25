package de.culture4life.luca.ui.lucaconnect

import androidx.fragment.app.Fragment
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.lucaconnect.children.*

class LucaConnectBottomSheetDialogFragment : BaseFlowBottomSheetDialogFragment<LucaConnectFlowPage, LucaConnectBottomSheetViewModel>() {

    override fun getViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = false

    override fun mapPageToFragment(page: LucaConnectFlowPage): Fragment {
        return when (page) {
            is LucaConnectFlowPage.ExplanationPage -> ExplanationFragment.newInstance()
            is LucaConnectFlowPage.ProvideProofPage -> ProvideProofFragment.newInstance()
            is LucaConnectFlowPage.LucaConnectSharedDataPage -> LucaConnectSharedDataFragment.newInstance()
            is LucaConnectFlowPage.KritisPage -> KritisFragment.newInstance()
            is LucaConnectFlowPage.LucaConnectConsentPage -> LucaConnectConsentFragment.newInstance()
            is LucaConnectFlowPage.ConnectSuccessPage -> ConnectSuccessFragment.newInstance()
        }
    }

    companion object {
        const val TAG = "LucaConnectBottomSheetDialogFragment"

        fun newInstance(): LucaConnectBottomSheetDialogFragment = LucaConnectBottomSheetDialogFragment()
    }
}
