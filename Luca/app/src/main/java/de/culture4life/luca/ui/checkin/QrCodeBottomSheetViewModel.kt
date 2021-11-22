package de.culture4life.luca.ui.checkin

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.checkin.CheckInManager.KEY_INCLUDE_ENTRY_POLICY
import de.culture4life.luca.document.DocumentManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewEvent
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class QrCodeBottomSheetViewModel(app: Application) : BaseViewModel(app) {

    private var documentManager: DocumentManager = application.documentManager

    val onBottomSheetClosed: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val onDebuggingCheckInRequested: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()
    val includeEntryPolicy: MutableLiveData<Boolean> = MutableLiveData()
    val onDocumentsUnavailable: MutableLiveData<ViewEvent<Boolean>> = MutableLiveData()

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(documentManager.initialize(application))
            .andThen(preferencesManager.restoreOrDefault(KEY_INCLUDE_ENTRY_POLICY, false)
                .flatMapCompletable { updateIfRequired(includeEntryPolicy, it) })
    }

    // TODO: 01.11.21 use keepDataUpdated as soon as QrCodeBottomSheetFragment implements BaseFragment methods
    /*
    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            preferencesManager.restoreOrDefaultAndGetChanges(KEY_INCLUDE_ENTRY_POLICY, false)
                .flatMapCompletable { updateIfRequired(includeEntryPolicy, it) }
        )
    }
    */

    fun onQrCodeBottomSheetClosed() {
        updateAsSideEffect(onBottomSheetClosed, ViewEvent(true))
    }

    fun onDebuggingCheckInRequested() {
        updateAsSideEffect(onDebuggingCheckInRequested, ViewEvent(true))
    }

    fun onIncludeEntryPolicyToggled(enabled: Boolean) {
        hasIncludableDocument()
            .flatMapCompletable { documentAvailable ->
                if (documentAvailable || !enabled) {
                    preferencesManager.persist(KEY_INCLUDE_ENTRY_POLICY, enabled)
                        .andThen(updateIfRequired(includeEntryPolicy, enabled))
                } else {
                    update(onDocumentsUnavailable, ViewEvent(true))
                        .andThen(updateIfRequired(includeEntryPolicy, false))
                }
            }
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    private fun hasIncludableDocument(): Single<Boolean> {
        return Single.mergeArray(
            documentManager.hasVaccinationDocument(),
            documentManager.hasRecoveryDocument(),
            documentManager.hasPcrTestDocument(),
            documentManager.hasQuickTestDocument()
        ).any { hasDocument -> hasDocument }
    }

}