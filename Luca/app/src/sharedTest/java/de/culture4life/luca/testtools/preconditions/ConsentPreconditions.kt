package de.culture4life.luca.testtools.preconditions

import androidx.test.core.app.ApplicationProvider
import de.culture4life.luca.LucaApplication

class ConsentPreconditions {

    private val application = ApplicationProvider.getApplicationContext<LucaApplication>()
    private val consentManager by lazy { application.getInitializedManager(application.consentManager).blockingGet() }

    fun givenConsent(consent: String, isGiven: Boolean) {
        with(consentManager) {
            getConsent(consent)
                .flatMapCompletable { persistConsent(it.copy(approved = isGiven)) }
                .blockingAwait()
        }
    }
}
