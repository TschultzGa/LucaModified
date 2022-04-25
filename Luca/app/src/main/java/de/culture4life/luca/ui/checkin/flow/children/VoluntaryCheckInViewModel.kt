package de.culture4life.luca.ui.checkin.flow.children

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.ui.checkin.flow.CheckInFlowViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class VoluntaryCheckInViewModel(app: Application) : BaseFlowChildViewModel(app) {

    fun onActionButtonClicked(checkInAnonymously: Boolean, alwaysVoluntary: Boolean) {
        persistSettings(alwaysVoluntary, checkInAnonymously)
            .andThen(
                Completable.fromAction {
                    (sharedViewModel as CheckInFlowViewModel?)?.checkInAnonymously = checkInAnonymously
                }
            )
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                sharedViewModel?.navigateToNext()
            }
            .addTo(modelDisposable)
    }

    private fun persistSettings(alwaysVoluntary: Boolean, checkInAnonymously: Boolean): Completable {
        return Completable.mergeArray(
            preferencesManager.persist(KEY_ALWAYS_CHECK_IN_VOLUNTARY, alwaysVoluntary),
            Completable.defer {
                if (alwaysVoluntary) {
                    preferencesManager.persist(KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, checkInAnonymously)
                } else {
                    Completable.complete()
                }
            }
        )
    }

    companion object {
        const val KEY_ALWAYS_CHECK_IN_VOLUNTARY = "always_check_in_voluntary"
        const val KEY_ALWAYS_CHECK_IN_ANONYMOUSLY = "always_check_in_anonymously"
    }
}
