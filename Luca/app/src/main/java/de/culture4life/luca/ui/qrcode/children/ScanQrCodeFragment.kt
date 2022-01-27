package de.culture4life.luca.ui.qrcode.children

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentScanQrCodeBinding
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel

class ScanQrCodeFragment : BaseQrCodeFlowChildFragment<ScanQrCodeViewModel, AddCertificateFlowViewModel>() {

    private lateinit var binding: FragmentScanQrCodeBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentScanQrCodeBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<ScanQrCodeViewModel> = ScanQrCodeViewModel::class.java
    override fun getSharedViewModelClass(): Class<AddCertificateFlowViewModel> = AddCertificateFlowViewModel::class.java

    override fun initializeCameraPreview() {
        super.initializeCameraPreview()
        cameraPreviewView = binding.cameraPreviewView
        binding.cameraContainerConstraintLayout.setOnClickListener {
            showCameraPreview(requestConsent = true, requestPermission = true)
        }
    }

    override fun setCameraPreviewVisible(isVisible: Boolean) {
        super.setCameraPreviewVisible(isVisible)
        binding.cameraContainerConstraintLayout.background = ContextCompat.getDrawable(
            requireContext(),
            if (isVisible) {
                R.drawable.bg_camera_box_active_preview
            } else {
                R.drawable.bg_camera_box
            }
        )
        binding.startCameraLinearLayout.isVisible = !isVisible
    }

    companion object {
        fun newInstance(): ScanQrCodeFragment {
            return ScanQrCodeFragment()
        }
    }
}