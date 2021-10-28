package de.culture4life.luca.ui.checkin

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent

class VoluntaryCheckInViewModel(app: Application) : BaseViewModel(app) {

    val onViewDismissed: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val onVoluntaryCheckInButtonPressed: MutableLiveData<ViewEvent<VoluntaryCheckInResponse>> = MutableLiveData()

    fun onViewDismissed() {
        updateAsSideEffect(onViewDismissed, ViewEvent(true))
    }

    fun onVoluntaryCheckInButtonPressed(shareContactData: Boolean, url: String) {
        updateAsSideEffect(onVoluntaryCheckInButtonPressed, ViewEvent(VoluntaryCheckInResponse(shareContactData, url)))
    }

    data class VoluntaryCheckInResponse(
        val shareContactData: Boolean,
        val url: String
    )
}