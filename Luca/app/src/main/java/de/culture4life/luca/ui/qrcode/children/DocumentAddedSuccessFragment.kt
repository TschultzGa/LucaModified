package de.culture4life.luca.ui.qrcode.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentDocumentAddedSuccessBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel

class DocumentAddedSuccessFragment : BaseFlowChildFragment<DocumentAddedSuccessViewModel, AddCertificateFlowViewModel>() {

    private lateinit var binding: FragmentDocumentAddedSuccessBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentDocumentAddedSuccessBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<DocumentAddedSuccessViewModel> = DocumentAddedSuccessViewModel::class.java
    override fun getSharedViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.actionButton.setOnClickListener { viewModel.onActionButtonPressed() }
    }

    companion object {
        fun newInstance(): DocumentAddedSuccessFragment {
            return DocumentAddedSuccessFragment()
        }
    }
}