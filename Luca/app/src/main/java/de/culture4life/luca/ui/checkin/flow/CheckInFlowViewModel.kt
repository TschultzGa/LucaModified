package de.culture4life.luca.ui.checkin.flow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel
import de.culture4life.luca.ui.checkin.CheckInViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

class CheckInFlowViewModel(app: Application) : BaseFlowViewModel(app) {

    var url: String? = null
    var locationResponseData: LocationResponseData? = null

    var shareEntryPolicyState: Boolean = false
    var checkInAnonymously: Boolean = true

    val onCheckInRequested: MutableLiveData<ViewEvent<CheckInRequest>> = MutableLiveData()

    override fun initialize(): Completable {
        return super.initialize()
    }

    fun initializeViewModel() {
        return initializeUserSetting()
            .andThen(updatePages())
            .onErrorComplete()
            .subscribe()
            .addTo(modelDisposable)
    }

    private fun updatePages(): Completable {
        return Completable
            .fromCallable { pages.clear() }
            .andThen(
                Maybe.concat(
                    createConfirmCheckInPageIfRequired().doOnSuccess { pages.add(it) },
                    createVoluntaryCheckInPageIfRequired().doOnSuccess { pages.add(it) },
                    createEntryPolicyPageIfRequired().doOnSuccess { pages.add(it) }
                )
            )
            .ignoreElements()
            .doOnComplete {
                updateAsSideEffect(onPagesUpdated, ViewEvent(pages))
            }
    }

    private fun initializeUserSetting(): Completable {
        return Single.mergeArray(
            preferencesManager.restoreOrDefault(VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_ANONYMOUSLY, true)
                .doOnSuccess { checkInAnonymously = it },
            preferencesManager.restoreOrDefault(EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false)
                .doOnSuccess { shareEntryPolicyState = it }
        ).ignoreElements()
    }

    private fun createConfirmCheckInPageIfRequired(): Maybe<BaseCheckInFlowFragment<*, *>> {
        return preferencesManager.restoreOrDefault(ConfirmCheckInViewModel.KEY_SKIP_CHECK_IN_CONFIRMATION, false)
            .flatMapMaybe { skipCheckInConfirm ->
                Maybe.fromCallable {
                    if ((locationResponseData?.isContactDataMandatory == true || CheckInViewModel.FEATURE_ANONYMOUS_CHECKIN_DISABLED) && !skipCheckInConfirm) {
                        ConfirmCheckInFragment.newInstance(locationResponseData?.groupName)
                    } else {
                        null
                    }
                }
            }
    }

    private fun createVoluntaryCheckInPageIfRequired(): Maybe<BaseCheckInFlowFragment<*, *>> {
        return preferencesManager.restoreOrDefault(VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_VOLUNTARY, false)
            .flatMapMaybe { alwaysCheckInVoluntary ->
                Maybe.fromCallable {
                    if ((locationResponseData?.isContactDataMandatory == false && !alwaysCheckInVoluntary) &&
                        !CheckInViewModel.FEATURE_ANONYMOUS_CHECKIN_DISABLED
                    ) {
                        VoluntaryCheckInFragment.newInstance()
                    } else {
                        null
                    }
                }
            }
    }

    private fun createEntryPolicyPageIfRequired(): Maybe<BaseCheckInFlowFragment<*, *>> {
        return preferencesManager.restoreOrDefault(EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false)
            .flatMapMaybe { alwaysShareEntryPolicyStatus ->
                Maybe.fromCallable {
                    if ((locationResponseData?.entryPolicy != null && !alwaysShareEntryPolicyStatus) &&
                        !CheckInViewModel.FEATURE_ENTRY_POLICY_CHECKIN_DISABLED
                    ) {
                        EntryPolicyFragment.newInstance()
                    } else {
                        null
                    }
                }
            }
    }

    fun requestCheckIn() {
        val checkInRequest = CheckInRequest(
            url = url!!,
            isAnonymous = checkInAnonymously && !CheckInViewModel.FEATURE_ANONYMOUS_CHECKIN_DISABLED,
            shareEntryPolicyStatus = shareEntryPolicyState && !CheckInViewModel.FEATURE_ENTRY_POLICY_CHECKIN_DISABLED
        )

        updateAsSideEffect(onCheckInRequested, ViewEvent(checkInRequest))
    }

    override fun onFinishFlow() {
        requestCheckIn()
        dismissBottomSheet()
    }

    data class CheckInRequest(
        val url: String,
        val isAnonymous: Boolean,
        val shareEntryPolicyStatus: Boolean
    )
}
