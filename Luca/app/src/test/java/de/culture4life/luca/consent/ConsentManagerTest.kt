package de.culture4life.luca.consent

import de.culture4life.luca.LucaUnitTest
import io.reactivex.rxjava3.core.Completable
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ConsentManagerTest : LucaUnitTest() {

    private val consentManager = spy(getInitializedManager(application.consentManager))

    @Test
    fun `Consent is un-approved by default`() {
        val consentObserver = consentManager.getConsent("test")
            .test()

        triggerScheduler()

        consentObserver.assertValue { !it.approved && it.lastDisplayTimestamp == 0L }
    }

    @Test
    fun `Consent can be retrieved after being persisted`() {
        val consent = Consent("test")
        consentManager.persistConsent(consent)
            .andThen(consentManager.getConsent(consent.id))
            .test()
            .await()
            .assertValue(consent)
    }

    @Test
    fun `Updating consent notifies existing observers`() {
        val consent = Consent("test", true)

        // consent not approved yet
        val initialConsentObserver = consentManager.getConsentAndChanges(consent.id)
            .firstOrError()
            .test()

        triggerScheduler()

        initialConsentObserver.assertValue { !it.approved }

        val changeObserver = consentManager.getConsentAndChanges(consent.id)
            .skip(1)
            .test()
            .assertNoValues()
            .assertNotComplete()

        // consent approved
        consentManager.persistConsent(consent)
            .test()
            .await()
            .assertComplete()

        // existing observer updated with approved consent
        changeObserver.assertValue(consent)

        // new observer receives only approved consent
        consentManager.getConsent(consent.id)
            .test()
            .assertValue(consent)
    }

    @Test
    fun `Asserting approved consent is approved succeeds`() {
        val consent = Consent("test", true)
        consentManager.persistConsent(consent)
            .andThen(consentManager.assertConsentApproved(consent.id))
            .test()
            .assertComplete()
    }

    @Test
    fun `Asserting un-approved consent is approved errors`() {
        val consent = Consent("test", false)
        consentManager.persistConsent(consent)
            .andThen(consentManager.assertConsentApproved(consent.id))
            .test()
            .assertError(MissingConsentException::class.java)
    }

    @Test
    fun `Consent requested if required`() {
        val consent = Consent("test", false)
        var consentRequested = false
        val mockedConsentRequest = Completable.fromAction { consentRequested = true }
        whenever(consentManager.requestConsent(consent.id)).thenReturn(mockedConsentRequest)

        consentManager.persistConsent(consent)
            .andThen(consentManager.requestConsentIfRequired(consent.id))
            .test()
            .assertComplete()

        Assert.assertTrue("Consent not requested", consentRequested)
    }

    @Test
    fun `Consent not requested if not required`() {
        val consent = Consent("test", true)
        var consentRequested = false
        val mockedConsentRequest = Completable.fromAction { consentRequested = true }
        whenever(consentManager.requestConsent(consent.id)).thenReturn(mockedConsentRequest)

        consentManager.persistConsent(consent)
            .andThen(consentManager.requestConsentIfRequiredAndGetResult(consent.id))
            .test()
            .assertComplete()

        Assert.assertFalse("Consent requested", consentRequested)
    }

    @Test
    fun `Consent result emitted after processed request`() {
        val consent = Consent("test", true)
        whenever(consentManager.requestConsent(consent.id)).thenReturn(Completable.complete())

        val requestObserver = consentManager.requestConsentIfRequiredAndGetResult(consent.id)
            .test()
            .assertNoValues()

        consentManager.processConsentRequestResult(consent.id, consent.approved)
            .delaySubscription(3, TimeUnit.SECONDS) // result is not available instantly after request
            .test()

        advanceScheduler(3, TimeUnit.SECONDS)

        requestObserver.assertValue {
            it.id == consent.id &&
                it.approved == consent.approved &&
                it.lastDisplayTimestamp > consent.lastDisplayTimestamp
        }
    }
}
