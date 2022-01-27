package de.culture4life.luca.ui.checkin.flow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class EntryPolicyViewModel(app: Application) : BaseFlowChildViewModel(app) {

    val onConsentValueChanged: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    fun onActionButtonClicked(shareEntryPolicyStatus: Boolean, alwaysShare: Boolean) {
        persistAlwaysShareEntryPolicyStatus(alwaysShare)
            .andThen(Completable.fromAction {
                (sharedViewModel as CheckInFlowViewModel?)?.shareEntryPolicyState = shareEntryPolicyStatus
            })
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                sharedViewModel?.navigateToNext()
            }
            .addTo(modelDisposable)
    }

    fun onEntryPolicySharingConsented() {
        updateAsSideEffect(onConsentValueChanged, ViewEvent(true))
    }

    fun onEntryPolicySharingNotConsented() {
        updateAsSideEffect(onConsentValueChanged, ViewEvent(false))
    }

    private fun persistAlwaysShareEntryPolicyStatus(alwaysShare: Boolean): Completable {
        return preferencesManager.persist(KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, alwaysShare)
    }

    companion object {
        const val KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS = "always_share_entry_policy_status"
    }

}