package de.culture4life.luca.ui.venue

import android.app.Application
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.ui.base.BaseBottomSheetViewModel
import io.reactivex.rxjava3.core.Completable

class VenueUrlConsentViewModel(app: Application) : BaseBottomSheetViewModel(app) {

    private val consentManager = this.application.consentManager

    override fun initialize(): Completable {
        return super.initialize().andThen(
            consentManager.initialize(application)
        )
    }

    fun onActionButtonClicked(url: String?, dontAskAgain: Boolean) {
        invoke(consentManager.processConsentRequestResult(ConsentManager.ID_OPEN_VENUE_URL, dontAskAgain))
            .subscribe {
                url?.let { application.openUrl(it) }
            }
    }
}
