package de.culture4life.luca.testtools.pages

import de.culture4life.luca.R
import io.github.kakaocup.kakao.text.KTextView

class LucaIdVerificationPage {
    val revocationCode = KTextView { withId(R.id.revocationCodeTextView) }
}
