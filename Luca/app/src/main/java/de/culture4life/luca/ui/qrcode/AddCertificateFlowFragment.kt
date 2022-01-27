package de.culture4life.luca.ui.qrcode

import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.document.Document
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.qrcode.children.DocumentAddedSuccessFragment
import de.culture4life.luca.ui.qrcode.children.ScanQrCodeFragment
import de.culture4life.luca.ui.qrcode.children.SelectInputFragment
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

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
        viewModel.parsedDocument.observe(viewLifecycleOwner, {
            if (!it.hasBeenHandled()) {
                showDocumentImportConsentDialog(it.valueAndMarkAsHandled)
            }
        })

        viewModel.addedDocument.observe(viewLifecycleOwner, {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                if (binding.confirmationStepViewPager.currentItem == 0) {
                    pagerAdapter.removePageAtIndex(1) // remove ScanQrCodeFragment
                }
                navigateToNext()
            }
        })
    }

    private fun showDocumentImportConsentDialog(document: Document) {
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.document_import_action)
            .setMessage(R.string.document_import_consent)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                viewModel.addDocument(document)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Timber.i("Document added: %s", document) },
                        { Timber.w("Unable to add document: $it") }
                    )
                    .addTo(viewDisposable)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> })
            .show()
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