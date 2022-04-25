package de.culture4life.luca.ui.qrcode

import android.content.DialogInterface
import androidx.fragment.app.Fragment
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.qrcode.children.DocumentAddedSuccessFragment
import de.culture4life.luca.ui.qrcode.children.ScanQrCodeFragment
import de.culture4life.luca.ui.qrcode.children.SelectInputFragment

class AddCertificateFlowFragment : BaseFlowBottomSheetDialogFragment<AddCertificateFlowPage, AddCertificateFlowViewModel>() {
    override fun getViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = false

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        viewModel.addedDocument.observe(viewLifecycleOwner) {
            if (it.isNotHandled) {
                it.isHandled = true
                if (binding.confirmationStepViewPager.currentItem == 0) {
                    pagerAdapter.removePageAtIndex(1) // remove ScanQrCodeFragment
                }
                navigateToNext()
            }
        }
    }

    override fun mapPageToFragment(page: AddCertificateFlowPage): Fragment {
        return when (page) {
            is AddCertificateFlowPage.SelectInputPage -> SelectInputFragment.newInstance()
            is AddCertificateFlowPage.ScanQrCodePage -> ScanQrCodeFragment.newInstance()
            is AddCertificateFlowPage.DocumentAddedSuccessPage -> DocumentAddedSuccessFragment.newInstance()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.onAddCertificateViewDismissed()
    }

    companion object {
        const val TAG = "AddCertificateFlowFragment"

        fun newInstance(): AddCertificateFlowFragment = AddCertificateFlowFragment()
    }
}
