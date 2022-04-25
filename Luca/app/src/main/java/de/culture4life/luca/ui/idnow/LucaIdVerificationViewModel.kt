package de.culture4life.luca.ui.idnow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.whatisnew.WhatIsNewManager
import io.reactivex.rxjava3.core.Completable

class LucaIdVerificationViewModel(application: Application) : BaseViewModel(application) {

    private val idNowManager = this.application.idNowManager
    private val whatIsNewManager = this.application.whatIsNewManager

    val revocationCode = MutableLiveData<String>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    idNowManager.initialize(application),
                    whatIsNewManager.initialize(application)
                )
            )
            .andThen(invoke(updateRevocationCode()))
            .andThen(invoke(markVerificationSuccessfulMessageAsSeen()))
    }

    private fun updateRevocationCode() = idNowManager.getRevocationCode().flatMapCompletable { update(revocationCode, it) }

    private fun markVerificationSuccessfulMessageAsSeen() =
        whatIsNewManager.markMessageAsSeen(WhatIsNewManager.ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE)
}
