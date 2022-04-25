package de.culture4life.luca.ui.account.entrypolicy

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.checkin.flow.children.EntryPolicyViewModel.Companion.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable

class EntryPolicyPreferencesViewModel(application: Application) : BaseViewModel(application) {

    val shareEntryPolicyAlways = MutableLiveData(false)

    override fun initialize(): Completable {
        return super.initialize().andThen(updateInitialStatusImmediately())
    }

    private fun updateInitialStatusImmediately(): Completable {
        return preferencesManager.restoreOrDefault(KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { shareEntryPolicyAlways.value = it }
            .ignoreElement()
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepShareEntryPolicyUpdated()
        )
    }

    private fun keepShareEntryPolicyUpdated(): Completable {
        return preferencesManager.getChanges(KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, Boolean::class.java)
            .flatMapCompletable { update(shareEntryPolicyAlways, it) }
    }

    fun onEntryPolicyToggled(enabled: Boolean) {
        invoke(preferencesManager.persist(KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, enabled)).subscribe()
    }
}
