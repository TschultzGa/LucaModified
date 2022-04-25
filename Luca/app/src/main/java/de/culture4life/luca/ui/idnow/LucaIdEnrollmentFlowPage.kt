package de.culture4life.luca.ui.idnow

import android.os.Bundle
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage

sealed class LucaIdEnrollmentFlowPage(override val arguments: Bundle? = null) : BaseFlowPage {
    object ExplanationPage : LucaIdEnrollmentFlowPage()
    object ConsentPage : LucaIdEnrollmentFlowPage()
    object SuccessPage : LucaIdEnrollmentFlowPage()
}
