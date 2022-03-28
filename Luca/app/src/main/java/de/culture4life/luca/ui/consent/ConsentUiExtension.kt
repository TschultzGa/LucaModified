package de.culture4life.luca.ui.consent

import androidx.fragment.app.FragmentManager
import de.culture4life.luca.consent.ConsentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class ConsentUiExtension(
    private val fragmentManager: FragmentManager,
    private val consentManager: ConsentManager,
    private val disposable: CompositeDisposable
) {

    init {
        initializeConsentRequests()
    }

    private fun initializeConsentRequests() {
        disposable.add(
            consentManager.getConsentRequests()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { id: String ->
                    showConsentRequest(id)
                }
        )
    }

    private fun showConsentRequest(id: String) {
        val fragment = ConsentBottomSheetFragment.newInstance(id)
        val tag = "consent_$id"
        fragment.show(fragmentManager, tag)
    }
}
