package de.culture4life.luca.ui.idnow.children

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel

class ExplanationViewModel(app: Application) : BaseFlowChildViewModel(app) {
    fun onActionButtonClicked() {
        sharedViewModel!!.navigateToNext()
    }
}
