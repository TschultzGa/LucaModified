package de.culture4life.luca.ui.qrcode

import android.os.Bundle
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage

sealed class AddCertificateFlowPage(override val arguments: Bundle? = null) : BaseFlowPage {
    object SelectInputPage : AddCertificateFlowPage()
    object ScanQrCodePage : AddCertificateFlowPage()
    object DocumentAddedSuccessPage : AddCertificateFlowPage()
}
