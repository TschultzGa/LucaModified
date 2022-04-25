package de.culture4life.luca.ui.account.news

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent
import io.reactivex.rxjava3.core.Completable

class NewsViewModel(app: Application) : BaseViewModel(app) {
    val lucaIdEnabledStatus = MutableLiveData<ViewEvent<Boolean>>()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(updateLucaIdEnabledStatus())
    }

    private fun updateLucaIdEnabledStatus(): Completable {
        return application.idNowManager.isEnrollmentEnabled()
            .flatMapCompletable { enabled ->
                update(lucaIdEnabledStatus, ViewEvent(enabled))
            }
    }
}
