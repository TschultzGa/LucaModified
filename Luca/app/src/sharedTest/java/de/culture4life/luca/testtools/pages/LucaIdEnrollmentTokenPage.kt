package de.culture4life.luca.testtools.pages

import de.culture4life.luca.R
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView

class LucaIdEnrollmentTokenPage {
    val enrollmentToken = KTextView { withId(R.id.enrollmentTokenTextView) }
    val actionButton = KButton { withId(R.id.actionButton) }
}
