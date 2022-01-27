package de.culture4life.luca.ui.checkin.flow

import android.app.Application
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class ConfirmCheckInViewModel(app: Application) : BaseFlowChildViewModel(app) {

    fun onActionButtonClicked(dontAskAgain: Boolean) {
        persistSkipCheckInConfirmation(dontAskAgain)
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                sharedViewModel?.navigateToNext()
            }
            .addTo(modelDisposable)
    }

    private fun persistSkipCheckInConfirmation(skipCheckInConfirmation: Boolean): Completable {
        return preferencesManager.persist(KEY_SKIP_CHECK_IN_CONFIRMATION, skipCheckInConfirmation)
    }

    companion object {
        const val KEY_SKIP_CHECK_IN_CONFIRMATION = "dont_ask_confirmation"
        const val KEY_LOCATION_NAME = "locationName"
    }
}