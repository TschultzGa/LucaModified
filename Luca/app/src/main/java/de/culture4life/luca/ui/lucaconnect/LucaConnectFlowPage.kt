package de.culture4life.luca.ui.lucaconnect

import android.os.Bundle
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowPage

sealed class LucaConnectFlowPage(override val arguments: Bundle? = null) : BaseFlowPage {
    object ExplanationPage : LucaConnectFlowPage()
    object ProvideProofPage : LucaConnectFlowPage()
    object LucaConnectSharedDataPage : LucaConnectFlowPage()
    object KritisPage : LucaConnectFlowPage()
    object LucaConnectConsentPage : LucaConnectFlowPage()
    object ConnectSuccessPage : LucaConnectFlowPage()
}
