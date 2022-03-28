package de.culture4life.luca.testtools.pages

import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import de.culture4life.luca.R
import de.culture4life.luca.testtools.pages.dialogs.BirthdayNotMatchDialog
import de.culture4life.luca.testtools.pages.dialogs.DocumentImportConsentDialog
import de.culture4life.luca.testtools.pages.dialogs.UpdateDialog
import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.ui.myluca.MyLucaFragment
import de.culture4life.luca.ui.myluca.MyLucaListItem
import io.github.kakaocup.kakao.recycler.KRecyclerItem
import io.github.kakaocup.kakao.recycler.KRecyclerView
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import org.hamcrest.Matcher

class MyLucaPage {

    val title = KTextView { withId(R.id.emptyTitleTextView) }
    val addDocumentButton = KButton { withId(R.id.primaryActionButton) }
    val addCertificateFlowPage = AddCertificateFlowPage()
    val documentList = KRecyclerView(
        { withId(R.id.myLucaRecyclerView) },
        { itemType(MyLucaPage::DocumentItem) }
    )
    val updateDialog = UpdateDialog()
    val consentsDialog = DocumentImportConsentDialog()
    val birthdayNotMatchDialog = BirthdayNotMatchDialog()

    fun navigateToScanner() {
        addDocumentButton.click()
        addCertificateFlowPage.run {
            scanQrCodeButton.run {
                scrollTo()
                click()
            }
        }
    }

    fun stepsScanValidDocument(scenario: FragmentScenario<MyLucaFragment>, document: SampleDocuments) {
        navigateToScanner()
        addCertificateFlowPage.run {
            qrCodeScannerPage.run {
                scanQrCode(scenario, document.qrCodeContent)
                consentsDialog.acceptButton.click()
            }
            successConfirmButton.scrollTo()
            successConfirmButton.click()
        }
    }

    class DocumentItem(parent: Matcher<View>) : KRecyclerItem<MyLucaListItem>(parent) {
        val title = KTextView(parent) { withId(R.id.itemTitleTextView) }
    }
}
