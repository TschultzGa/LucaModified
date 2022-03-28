package de.culture4life.luca.ui.checkin.flow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.BaseBottomSheetViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class ConsentEntryPolicySharingViewModel(app: Application) : BaseBottomSheetViewModel(app) {

    val onEntryPolicySharingConsentResult: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    fun onConsentAccepted(consented: Boolean) {
        persistEntryPolicyStateSharingConsented(consented)
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                updateAsSideEffect(onEntryPolicySharingConsentResult, ViewEvent(consented))
            }
            .addTo(modelDisposable)
    }

    private fun persistEntryPolicyStateSharingConsented(consented: Boolean): Completable {
        return preferencesManager.persist(KEY_ENTRY_POLICY_STATE_SHARING_CONSENTED, consented)
    }

    companion object {
        const val KEY_ENTRY_POLICY_STATE_SHARING_CONSENTED = "entryPolicyStateSharingConsented"
    }
}
