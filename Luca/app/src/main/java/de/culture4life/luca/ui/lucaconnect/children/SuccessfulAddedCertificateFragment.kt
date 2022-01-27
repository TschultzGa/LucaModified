package de.culture4life.luca.ui.lucaconnect.children


import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel

class SuccessfulAddedCertificateFragment : BaseFlowChildFragment<SuccessfulAddedCertificateViewModel, LucaConnectBottomSheetViewModel>() {

    override fun getViewModelClass(): Class<SuccessfulAddedCertificateViewModel> = SuccessfulAddedCertificateViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    companion object {
        fun newInstance(): SuccessfulAddedCertificateFragment {
            return SuccessfulAddedCertificateFragment()
        }
    }
}