package de.culture4life.luca.ui.account.voluntarycheckin

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.checkin.flow.VoluntaryCheckInViewModel.Companion.KEY_ALWAYS_CHECK_IN_ANONYMOUSLY
import de.culture4life.luca.ui.checkin.flow.VoluntaryCheckInViewModel.Companion.KEY_ALWAYS_CHECK_IN_VOLUNTARY
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable

class VoluntaryCheckInPreferencesViewModel(application: Application) : BaseViewModel(application) {

    val alwaysCheckInVoluntary = MutableLiveData(false)
    val alwaysCheckInAnonymously = MutableLiveData(true)

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(updateInitialAlwaysVoluntaryStatus())
            .andThen(updateInitialAlwaysAnonymouslyStatus())
    }

    private fun updateInitialAlwaysVoluntaryStatus(): Completable {
        return preferencesManager.restoreOrDefault(KEY_ALWAYS_CHECK_IN_VOLUNTARY, false)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { alwaysCheckInVoluntary.value = it }
            .ignoreElement()
    }

    private fun updateInitialAlwaysAnonymouslyStatus(): Completable {
        return preferencesManager.restoreOrDefault(KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, true)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { alwaysCheckInAnonymously.value = it }
            .ignoreElement()
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepAlwaysCheckInVoluntaryUpdated(),
            keepAlwaysCheckInAnonymouslyUpdated()
        )
    }

    private fun keepAlwaysCheckInVoluntaryUpdated(): Completable {
        return preferencesManager.getChanges(KEY_ALWAYS_CHECK_IN_VOLUNTARY, Boolean::class.java)
            .flatMapCompletable { update(alwaysCheckInVoluntary, it) }
    }

    private fun keepAlwaysCheckInAnonymouslyUpdated(): Completable {
        return preferencesManager.getChanges(KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, Boolean::class.java)
            .flatMapCompletable { update(alwaysCheckInAnonymously, it) }
    }

    fun onVoluntaryCheckInToggled(enabled: Boolean) {
        invoke(preferencesManager.persist(KEY_ALWAYS_CHECK_IN_VOLUNTARY, enabled)).subscribe()
    }

    fun onVoluntaryCheckInShareToggled(enabled: Boolean) {
        invoke(preferencesManager.persist(KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, !enabled)).subscribe()
    }
}
