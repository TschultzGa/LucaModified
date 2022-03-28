package de.culture4life.luca.testtools.pages.dialogs

import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.matcher.ViewMatchers
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.R
import io.github.kakaocup.kakao.common.views.KBaseView
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import org.hamcrest.Matcher
import org.hamcrest.Matchers

abstract class DefaultConsentDialog {

    val title = KTextView { withId(R.id.consentHeaderTextView) }
        .also { it.inRoot { isDialog() } }

    val info = KTextView { withId(R.id.consentInfoTextView) }
        .also { it.inRoot { isDialog() } }

    val acceptButton = KButton { withId(R.id.acceptButton) }
        .also { it.inRoot { isDialog() } }
        .also(::interceptButtonOnPerform)

    val cancelButton = KButton { withId(R.id.cancelButton) }
        .also { it.inRoot { isDialog() } }
        .also(::interceptButtonOnPerform)

    /**
     * Check content (e.g. title/description) to ensure that is the dialog we did expect.
     *
     * Will be automatically called before performing any button action (e.g. click).
     */
    abstract fun isDisplayed()

    protected fun interceptButtonOnPerform(builder: KBaseView<KButton>) {
        builder.intercept {
            onPerform(true) { viewInteraction, viewAction ->
                isDisplayed()
                if (isSingleClickWithRobolectric(viewAction)) {
                    // Sometimes the default click does not trigger the click lister.
                    // Root cause could be when multiple "isDialog" roots exists (e.g. BottomSheet + AlertDialog)
                    viewInteraction.perform(DirectClickAction())
                } else {
                    viewInteraction.perform(viewAction)
                }
            }
        }
    }

    private fun isSingleClickWithRobolectric(viewAction: ViewAction): Boolean {
        return LucaApplication.isRunningUnitTests() &&
            viewAction is GeneralClickAction &&
            viewAction.description == "single click"
    }

    class DirectClickAction : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return Matchers.allOf(ViewMatchers.isClickable(), ViewMatchers.isEnabled())
        }

        override fun getDescription(): String {
            return "single click directly"
        }

        override fun perform(uiController: UiController, view: View) {
            view.performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }
}
