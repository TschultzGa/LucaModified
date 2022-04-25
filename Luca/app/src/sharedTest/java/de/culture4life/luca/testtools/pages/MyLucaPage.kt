package de.culture4life.luca.testtools.pages

import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.espresso.assertion.ViewAssertions.matches
import de.culture4life.luca.R
import de.culture4life.luca.testtools.matcher.CardViewMatchers
import de.culture4life.luca.testtools.pages.dialogs.*
import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.ui.myluca.MyLucaFragment
import io.github.kakaocup.kakao.common.views.KView
import io.github.kakaocup.kakao.image.KImageView
import io.github.kakaocup.kakao.recycler.KRecyclerItem
import io.github.kakaocup.kakao.recycler.KRecyclerView
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import org.hamcrest.Matcher

class MyLucaPage {

    val title = KTextView { withId(R.id.emptyTitleTextView) }
    val addDocumentButton = KButton { withId(R.id.primaryActionButton) }
    val addCertificateFlowPage = AddCertificateFlowPage()
    val addIdentityFlow = IdNowEnrollFlowPage()
    val documentList = KRecyclerView(
        { withId(R.id.myLucaRecyclerView) },
        {
            itemType(MyLucaPage::DocumentItem)
            itemType(MyLucaPage::IdentityItem)
            itemType(MyLucaPage::IdentityRequestQueuedItem)
            itemType(MyLucaPage::IdentityRequestedItem)
            itemType(MyLucaPage::CreateIdentityItem)
            itemType(MyLucaPage::NoDocumentsItem)
        }
    )
    val updateDialog = UpdateDialog()
    val consentsDialog = DocumentImportConsentDialog()
    val authenticationDialog = UserAuthenticationRequiredLucaIdIdentDialog()
    val authenticationNotActivatedDialog = UserAuthenticationNotActivatedDialog()
    val birthdayNotMatchDialog = BirthdayNotMatchDialog()
    val deleteDialog = DeleteEnrollmentDialog()

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

    class DocumentItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        val title = KTextView(parent) { withId(R.id.itemTitleTextView) }
        val collapseIndicator = KImageView { withId(R.id.collapseIndicator) }
        val expandedContent = KView { withId(R.id.collapseLayout) }

        fun assertIsExpectedItemType() {
            title.isDisplayed()
            collapseIndicator.isDisplayed()
        }

        fun assertIsCollapsed() {
            expandedContent.isNotDisplayed()
        }

        fun assertIsExpanded() {
            expandedContent.isDisplayed()
        }

        fun assertIsMarkedAsValid() {
            view.check(matches(CardViewMatchers.hasBackgroundColor(R.color.document_outcome_fully_vaccinated)))
        }

        fun assertIsMarkedAsPartially() {
            view.check(matches(CardViewMatchers.hasBackgroundColor(R.color.document_outcome_partially_vaccinated)))
        }

        fun assertIsMarkedAsPositive() {
            view.check(matches(CardViewMatchers.hasBackgroundColor(R.color.document_outcome_positive)))
        }

        fun assertIsMarkedAsNegative() {
            view.check(matches(CardViewMatchers.hasBackgroundColor(R.color.document_outcome_negative)))
        }

        fun assertIsMarkedAsExpired() {
            view.check(matches(CardViewMatchers.hasBackgroundColor(R.color.document_outcome_expired)))
        }
    }

    class CreateIdentityItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        val title = KTextView(parent) { withId(R.id.titleTextView) }
        val description = KTextView(parent) { withId(R.id.descriptionTextView) }
        fun assertIsExpectedItemType() {
            title.hasText(R.string.luca_id_empty_item_title)
            description.hasText(R.string.luca_id_empty_item_description)
        }
    }

    fun KRecyclerView.assertIsCreateLucaIdItemOnPosition(position: Int) {
        childAt<CreateIdentityItem>(position) { assertIsExpectedItemType() }
    }

    fun KRecyclerView.assertIsIdentityRequestQueuedItemOnPosition(position: Int) {
        childAt<IdentityRequestQueuedItem>(position) { assertIsExpectedItemType() }
    }

    class IdentityRequestQueuedItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        val title = KTextView(parent) { withId(R.id.titleTextView) }
        val description = KTextView(parent) { withId(R.id.descriptionTextView) }
        fun assertIsExpectedItemType() {
            title.hasText(R.string.luca_id_request_queued_item_title)
            description.hasText(R.string.luca_id_request_queued_item_description)
        }
    }

    class IdentityRequestedItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        val title = KTextView(parent) { withId(R.id.titleTextView) }
        val description = KTextView(parent) { withId(R.id.descriptionTextView) }
        val token = KTextView(parent) { withId(R.id.tokenTextView) }
        fun assertIsExpectedItemType() {
            title.hasText(R.string.luca_id_requested_item_title)
            description.hasText(R.string.luca_id_requested_item_description)
            token.hasAnyText()
        }
    }

    fun KRecyclerView.assertIsIdentityRequestedItemOnPosition(position: Int) {
        childAt<IdentityRequestedItem>(position) { assertIsExpectedItemType() }
    }

    class IdentityItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        val name = KTextView(parent) { withId(R.id.name) }
        fun assertIsExpectedItemType() {
            name.hasText(R.string.luca_id_card_name_blurry_placeholder)
        }
    }

    fun KRecyclerView.assertIsIdentityItemOnPosition(position: Int) {
        childAt<IdentityItem>(position) { assertIsExpectedItemType() }
    }

    class NoDocumentsItem(parent: Matcher<View>) : KRecyclerItem<Any>(parent) {
        // TODO add content to verify it is shown
    }
}
