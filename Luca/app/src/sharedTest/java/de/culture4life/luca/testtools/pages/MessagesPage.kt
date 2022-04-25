package de.culture4life.luca.testtools.pages

import androidx.test.espresso.DataInteraction
import de.culture4life.luca.R
import de.culture4life.luca.testtools.pages.dialogs.TermsOfServiceUpdateDialog
import de.culture4life.luca.ui.messages.MessageListItem
import io.github.kakaocup.kakao.list.KAbsListView
import io.github.kakaocup.kakao.list.KAdapterItem
import io.github.kakaocup.kakao.text.KTextView

class MessagesPage {

    val messageList = KAbsListView(
        { withId(R.id.messageListView) },
        { itemType(MessagesPage::MessageItem) }
    )
    val termsOfServiceUpdateDialog = TermsOfServiceUpdateDialog()

    class MessageItem(interaction: DataInteraction) : KAdapterItem<MessageListItem>(interaction) {
        val title = KTextView(interaction) { withId(R.id.itemTitleTextView) }
        val description = KTextView(interaction) { withId(R.id.itemDescriptionTextView) }

        fun assertIsEnrollmentTokenMessage() {
            title.hasText(R.string.notification_luca_id_enrollment_token_title)
        }

        fun assertIsEnrollmentErrorMessage() {
            title.hasText(R.string.notification_luca_id_enrollment_error_title)
        }

        fun assertIsPostalCodeMessage() {
            title.hasText(R.string.notification_postal_code_matching_title)
        }

        fun assertIsLucaIdVerificationSuccessMessage() {
            title.hasText(R.string.luca_id_verification_success_title_sub)
        }

        fun assertIsTermsUpdateMessage() {
            title.hasText(R.string.notification_terms_update_title)
            description.hasText(R.string.notification_terms_update_description)
        }
    }

    fun KAbsListView.assertShowsOnlyDefaultMessages() {
        childAt<MessageItem>(0) { assertIsPostalCodeMessage() }
        hasSize(1)
    }
}
