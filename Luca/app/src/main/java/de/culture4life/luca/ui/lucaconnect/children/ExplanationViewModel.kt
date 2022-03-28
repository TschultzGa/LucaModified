package de.culture4life.luca.ui.lucaconnect.children

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel

class ExplanationViewModel(app: Application) : BaseFlowChildViewModel(app) {
    fun onActionButtonClicked() {
        application.connectManager.invokePowChallengeSolving().subscribe()
        sharedViewModel?.navigateToNext()
    }
}
