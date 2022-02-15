package de.culture4life.luca.testtools.pages

import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import de.culture4life.luca.R
import de.culture4life.luca.testtools.pages.dialogs.DocumentImportConsentDialog
import de.culture4life.luca.ui.qrcode.AddCertificateFlowFragment
import de.culture4life.luca.ui.qrcode.children.ScanQrCodeFragment
import io.github.kakaocup.kakao.text.KButton

class AddCertificateFlowPage {

    val scanQrCodeButton = KButton { withId(R.id.scanQrCodeButton) }.also { it.inRoot { isDialog() } }
    val consentsDialog = DocumentImportConsentDialog()
    val successConfirmButton = KButton { withId(R.id.actionButton) }.also { it.inRoot { isDialog() } }

    fun <FRAGMENT : Fragment> scanQrCode(scenario: FragmentScenario<FRAGMENT>, qrCodeContent: String) {
        scenario.onFragment { parent ->
            val addCertificateFlowFragment = parent.childFragmentManager.findFragmentByTag(AddCertificateFlowFragment.TAG)!!
            val scanQrCodeFragment = addCertificateFlowFragment.childFragmentManager.fragments.find { it is ScanQrCodeFragment } as ScanQrCodeFragment
            scanQrCodeFragment.processBarcode(qrCodeContent)
                .blockingAwait()
        }
    }
}