package de.culture4life.luca.ui.checkin.flow

import android.os.Bundle
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage

sealed class CheckInFlowPage(override val arguments: Bundle? = null) : BaseFlowPage {
    data class ConfirmCheckInPage(override val arguments: Bundle) : CheckInFlowPage(arguments)
    object VoluntaryCheckInPage : CheckInFlowPage()
    object EntryPolicyPage : CheckInFlowPage()
}
