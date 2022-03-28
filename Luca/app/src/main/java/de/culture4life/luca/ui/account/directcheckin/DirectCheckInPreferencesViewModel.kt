package de.culture4life.luca.ui.account.directcheckin

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.checkin.flow.ConfirmCheckInViewModel.Companion.KEY_SKIP_CHECK_IN_CONFIRMATION
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable

class DirectCheckInPreferencesViewModel(application: Application) : BaseViewModel(application) {

    val directCheckInStatus = MutableLiveData(false)

    override fun initialize(): Completable {
        return super.initialize().andThen(updateInitialStatusImmediately())
    }

    private fun updateInitialStatusImmediately(): Completable {
        return preferencesManager.restoreOrDefault(KEY_SKIP_CHECK_IN_CONFIRMATION, false)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { directCheckInStatus.value = it }
            .ignoreElement()
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepDirectCheckInStatusUpdated()
        )
    }

    private fun keepDirectCheckInStatusUpdated(): Completable {
        return preferencesManager.getChanges(KEY_SKIP_CHECK_IN_CONFIRMATION, Boolean::class.java)
            .flatMapCompletable { update(directCheckInStatus, it) }
    }

    fun onDirectCheckInToggled(enabled: Boolean) {
        invoke(preferencesManager.persist(KEY_SKIP_CHECK_IN_CONFIRMATION, enabled)).subscribe()
    }
}
