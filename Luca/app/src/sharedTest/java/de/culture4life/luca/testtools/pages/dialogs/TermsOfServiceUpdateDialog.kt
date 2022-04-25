package de.culture4life.luca.testtools.pages.dialogs

import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.test.platform.app.InstrumentationRegistry
import de.culture4life.luca.R

class TermsOfServiceUpdateDialog : DefaultConsentDialog() {

    override fun isDisplayed() {
        title.hasText(R.string.consent_terms_of_service_title)
        info.hasText(getStringFromHtml(R.string.consent_terms_of_service_description))
        acceptButton.hasText(R.string.updated_terms_button_agree)
        acceptButton.isDisplayed()
        cancelButton.isNotDisplayed()
    }

    private fun getStringFromHtml(@StringRes resourceId: Int): String {
        return HtmlCompat.fromHtml(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
    }
}
