package de.culture4life.luca.ui.qrcode.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentScanQrCodeBinding
import de.culture4life.luca.ui.BaseQrCodeFragment
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel
import io.reactivex.rxjava3.core.Completable

class ScanQrCodeFragment : BaseFlowChildFragment<ScanQrCodeViewModel, AddCertificateFlowViewModel>() {

    private lateinit var binding: FragmentScanQrCodeBinding
    private lateinit var cameraFragment: BaseQrCodeFragment

    override fun getViewBinding(): ViewBinding {
        binding = FragmentScanQrCodeBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<ScanQrCodeViewModel> = ScanQrCodeViewModel::class.java
    override fun getSharedViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        initializeCameraPreview()
    }

    private fun initializeCameraPreview() {
        cameraFragment = binding.qrCodeScanner.getFragment()
        cameraFragment.setBarcodeResultCallback(viewModel)
        observe(viewModel.isLoading) { isLoading ->
            cameraFragment.showLoading(isLoading)
        }
        observe(viewModel.showCameraPreview) { shouldShowCamera ->
            if (shouldShowCamera) {
                cameraFragment.requestShowCameraPreview()
            } else {
                cameraFragment.requestHideCameraPreview()
            }
        }
    }

    // TODO Find better way to inject barcode content for automated tests.
    fun processBarcode(barcodeContent: String): Completable {
        return viewModel.processBarcode(barcodeContent)
    }

    companion object {
        fun newInstance(): ScanQrCodeFragment {
            return ScanQrCodeFragment()
        }
    }
}
