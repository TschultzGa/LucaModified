package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel

class ConnectSuccessViewModel(app: Application) : BaseFlowChildViewModel(app) {
    fun onActionButtonClicked() {
        sharedViewModel?.navigateToNext()
    }
}
