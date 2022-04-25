package de.culture4life.luca.ui.idnow

import android.app.Application
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.whatisnew.WhatIsNewManager
import io.reactivex.rxjava3.core.Completable

class LucaIdEnrollmentErrorViewModel(application: Application) : BaseViewModel(application) {

    private val whatIsNewManager = this.application.whatIsNewManager

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(whatIsNewManager.initialize(application))
            .andThen(invoke(markErrorMessageAsSeen()))
    }

    private fun markErrorMessageAsSeen() = whatIsNewManager.markMessageAsSeen(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE)
}
