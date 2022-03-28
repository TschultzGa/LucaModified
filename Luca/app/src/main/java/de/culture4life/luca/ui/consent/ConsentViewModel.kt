package de.culture4life.luca.ui.consent

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.base.BaseBottomSheetViewModel
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class ConsentViewModel(app: Application) : BaseBottomSheetViewModel(app) {

    private val consentManager: ConsentManager = application.consentManager

    /**
     * Dismissing the view should be treated as not approving the consent by default.
     * However, the view is also dismissed after the consent has been approved.
     * In this case the dismiss should not be treated as another consent result.
     * @see [onBottomSheetDismissed]
     */
    private var treatDismissAsConsentResult = true

    val consentId: MutableLiveData<String> = MutableLiveData()

    override fun processArguments(arguments: Bundle?): Completable {
        return super.processArguments(arguments)
            .andThen(Maybe.fromCallable<String> { arguments?.getString(KEY_CONSENT_ID) })
            .flatMapCompletable { update(consentId, it) }
    }

    override fun onBottomSheetDismissed() {
        if (treatDismissAsConsentResult) {
            onConsentResult(approved = false, alreadyDismissed = true)
        }
        treatDismissAsConsentResult = true // reset for next consent view
        super.onBottomSheetDismissed()
    }

    fun onAcceptButtonClicked() {
        onConsentResult(approved = true, alreadyDismissed = false)
    }

    fun onCancelButtonClicked() {
        onConsentResult(approved = false, alreadyDismissed = false)
    }

    private fun onConsentResult(approved: Boolean, alreadyDismissed: Boolean) {
        consentManager.processConsentRequestResult(consentId.value!!, approved)
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.d("Consent result processed: $approved")
                    if (!alreadyDismissed) {
                        treatDismissAsConsentResult = false
                        dismissBottomSheet()
                    }
                },
                {
                    Timber.w("Unable to process consent result: $it")
                    addError(
                        ViewError.Builder(application)
                            .withCause(it)
                            .removeWhenShown()
                            .build()
                    )
                }
            )
            .addTo(modelDisposable)
    }

    companion object {
        const val KEY_CONSENT_ID = "consent_id"
    }
}
