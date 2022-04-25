package de.culture4life.luca.testtools.pages

import androidx.annotation.IdRes
import de.culture4life.luca.R
import de.culture4life.luca.testtools.pages.dialogs.UserAuthenticationNotActivatedDialog
import de.culture4life.luca.testtools.pages.dialogs.UserAuthenticationRequiredLucaIdEnrollmentDialog
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView

class IdNowEnrollFlowPage {

    val explanationPage = ExplanationPage()
    val consentPage = ConsentPage()
    val successPage = SuccessPage()

    abstract class DefaultChildPage(
        @IdRes protected val rootId: Int,
        @IdRes titleId: Int,
        @IdRes descriptionId: Int
    ) {

        val title = KTextView {
            isDescendantOfA { withId(rootId) }
            withId(titleId)
        }
            .also { it.inRoot { isDialog() } }

        val description = KTextView {
            isDescendantOfA { withId(rootId) }
            withId(descriptionId)
        }
            .also { it.inRoot { isDialog() } }

        val actionButton = KButton {
            isDescendantOfA { withId(rootId) }
            withId(R.id.actionButton)
        }
            .also { it.inRoot { isDialog() } }

        val cancelButton = KButton {
            withId(R.id.cancelButton)
        }
            .also { it.inRoot { isDialog() } }

        val backButton = KButton {
            withId(R.id.backButton)
        }
            .also { it.inRoot { isDialog() } }
    }

    class ExplanationPage : DefaultChildPage(R.id.fragmentLucaIdExplanation, R.id.explanationHeaderTextView, R.id.explanationDescriptionTextView) {
        fun isDisplayed() {
            title.hasText(R.string.luca_id_explanation_title)
            description.hasText(R.string.luca_id_explanation_description)
            actionButton.hasText(R.string.luca_id_explanation_action)
            cancelButton.isDisplayed()
            backButton.isNotDisplayed()
        }
    }

    class ConsentPage : DefaultChildPage(R.id.fragmentLucaIdConsent, R.id.consentTitleTextView, R.id.consentDescriptionTextView) {

        val authenticationDialog = UserAuthenticationRequiredLucaIdEnrollmentDialog()
        val authenticationNotActivatedDialog = UserAuthenticationNotActivatedDialog()

        fun isDisplayed() {
            title.hasText(R.string.luca_id_consent_title)
            description.hasText(R.string.luca_id_consent_description)
            actionButton.hasText(R.string.luca_id_consent_action)
            cancelButton.isDisplayed()
            backButton.isDisplayed()
        }
    }

    class SuccessPage : DefaultChildPage(R.id.fragmentLucaIdSuccess, R.id.lucaIdSuccessTitleTextView, R.id.lucaIdSuccessDescriptionTextView) {
        fun isDisplayed() {
            title.hasText(R.string.luca_id_success_title)
            description.hasText(R.string.luca_id_success_description)
            actionButton.hasText(R.string.luca_id_success_action)
            cancelButton.isDisplayed()
            backButton.isNotDisplayed()
        }
    }
}
