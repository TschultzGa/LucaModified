package de.culture4life.luca.ui.checkin.flow

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class VoluntaryCheckInViewModel(app: Application) : BaseFlowChildViewModel(app) {

    fun onActionButtonClicked(checkInVoluntary: Boolean, alwaysVoluntary: Boolean) {
        persistAlwaysCheckInVoluntary(alwaysVoluntary)
            .andThen(Completable.fromAction {
                (sharedViewModel as CheckInFlowViewModel?)?.checkInVoluntary = checkInVoluntary
            })
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                sharedViewModel?.navigateToNext()
            }
            .addTo(modelDisposable)
    }

    private fun persistAlwaysCheckInVoluntary(alwaysVoluntary: Boolean): Completable {
        return preferencesManager.persist(KEY_ALWAYS_CHECK_IN_VOLUNTARY, alwaysVoluntary)
    }

    companion object {
        const val KEY_ALWAYS_CHECK_IN_VOLUNTARY = "always_check_in_voluntary"
    }
}