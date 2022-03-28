package de.culture4life.luca.ui.qrcode

import android.content.DialogInterface
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.qrcode.children.DocumentAddedSuccessFragment
import de.culture4life.luca.ui.qrcode.children.ScanQrCodeFragment
import de.culture4life.luca.ui.qrcode.children.SelectInputFragment

class AddCertificateFlowFragment : BaseFlowBottomSheetDialogFragment<AddCertificateFlowViewModel>() {
    override fun getViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = false

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
        pagerAdapter.addPage(SelectInputFragment.newInstance())
        pagerAdapter.addPage(ScanQrCodeFragment.newInstance())
        pagerAdapter.addPage(DocumentAddedSuccessFragment.newInstance())
    }

    private fun initializeObservers() {
        viewModel.addedDocument.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                if (binding.confirmationStepViewPager.currentItem == 0) {
                    pagerAdapter.removePageAtIndex(1) // remove ScanQrCodeFragment
                }
                navigateToNext()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.onAddCertificateViewDismissed()
    }

    companion object {
        const val TAG = "AddCertificateFlowFragment"

        fun newInstance(): AddCertificateFlowFragment {
            return AddCertificateFlowFragment()
        }
    }
}
