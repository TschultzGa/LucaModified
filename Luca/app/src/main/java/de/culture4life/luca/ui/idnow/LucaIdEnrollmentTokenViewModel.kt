package de.culture4life.luca.ui.idnow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.whatisnew.WhatIsNewManager
import io.reactivex.rxjava3.core.Completable

class LucaIdEnrollmentTokenViewModel(application: Application) : BaseViewModel(application) {

    private val idNowManager = this.application.idNowManager
    private val whatIsNewManager = this.application.whatIsNewManager

    val enrollmentToken = MutableLiveData<String>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    idNowManager.initialize(application),
                    whatIsNewManager.initialize(application)
                )
            )
            .andThen(invoke(updateEnrollmentToken()))
            .andThen(invoke(markTokenMessageAsSeen()))
    }

    private fun updateEnrollmentToken() = idNowManager.getEnrollmentToken().flatMapCompletable { update(enrollmentToken, it) }

    private fun markTokenMessageAsSeen() = whatIsNewManager.markMessageAsSeen(WhatIsNewManager.ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE)
}
