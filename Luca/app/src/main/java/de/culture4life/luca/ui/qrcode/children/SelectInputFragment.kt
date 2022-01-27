package de.culture4life.luca.ui.qrcode.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentSelectInputBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel

class SelectInputFragment : BaseFlowChildFragment<SelectInputViewModel, AddCertificateFlowViewModel>() {

    private lateinit var binding: FragmentSelectInputBinding

    override fun getViewModelClass(): Class<SelectInputViewModel> = SelectInputViewModel::class.java
    override fun getSharedViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentSelectInputBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.importImageButton.setOnClickListener {
            viewModel.importImage(getFileImportUri(arrayOf("image/*")))
        }

        binding.scanQrCodeButton.setOnClickListener {
            viewModel.onScanQrCodeSelected()
        }
    }

    companion object {
        fun newInstance(): SelectInputFragment {
            return SelectInputFragment()
        }
    }
}