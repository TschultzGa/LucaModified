package de.culture4life.luca.ui.qrcode.children

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel

class DocumentAddedSuccessViewModel(app: Application) : BaseFlowChildViewModel(app) {
    fun onActionButtonPressed() {
        sharedViewModel?.navigateToNext()
    }
}