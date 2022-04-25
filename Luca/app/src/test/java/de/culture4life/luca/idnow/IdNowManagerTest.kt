package de.culture4life.luca.idnow

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.attestation.AttestationManager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.network.endpoints.LucaIdEndpoints
import de.culture4life.luca.network.pojo.id.IdentStatusResponseData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.rollout.RolloutManager
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions.Companion.RECEIPT_JWS
import de.culture4life.luca.testtools.samples.IdNowSamples
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import junit.framework.Assert.assertEquals
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

class IdNowManagerTest : LucaUnitTest() {

    private lateinit var idNowManager: IdNowManager
    private lateinit var attestationManager: AttestationManager
    private lateinit var cryptoManager: CryptoManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var rolloutManager: RolloutManager
    private lateinit var consentManager: ConsentManager
    private lateinit var lucaIdEndpoints: LucaIdEndpoints

    @Before
    fun before() {
        lucaIdEndpoints = mock()
        attestationManager = spy(application.attestationManager)
        cryptoManager = spy(application.cryptoManager)
        rolloutManager = spy(application.rolloutManager)
        consentManager = spy(application.consentManager)
        val networkManagerSpy = spy(application.networkManager)
        whenever(networkManagerSpy.getLucaIdEndpoints()).thenReturn(Single.just(lucaIdEndpoints))

        idNowManager = spy(
            application.getInitializedManager(
                IdNowManager(
                    application.preferencesManager,
                    networkManagerSpy,
                    application.powManager,
                    cryptoManager,
                    application.whatIsNewManager,
                    attestationManager,
                    rolloutManager,
                    consentManager
                )
            ).blockingGet()
        )

        preferencesManager = application.preferencesManager
    }

    @Test
    fun `Upon receiving FAILURE state, ident is deleted`() {
        // Given
        doReturn(Single.just("Token123")).`when`(attestationManager).getToken()
        whenever(lucaIdEndpoints.deleteIdent(any())).thenReturn(Completable.complete())
        whenever(lucaIdEndpoints.getIdentStatus(any())).thenReturn(
            Single.just(
                IdentStatusResponseData(
                    IdentStatusResponseData.State.FAILED,
                    "revocationCode"
                )
            )
        )
        var deletionCallCount = 0
        doReturn(Completable.fromAction { deletionCallCount++ }).`when`(idNowManager).unEnroll()

        // When
        val testObserver = idNowManager.updateEnrollmentStatus().test().await()

        // Then
        testObserver.assertNoErrors()
        assertEquals(1, deletionCallCount)
    }

    @Test
    fun `Receipt JWS is verified successfully when JWT is valid and keys match`() {
        // Given
        val validBindingKey =
            Single.just("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEndnCOOtefOnMzEtLjSUOjfGN3NnV60s0eTDlD/5IM4Cf6Y7fT8x2Kxlva9EGgNmP4Y2C6Ism5+8nuDQsyBl5gg==")
        val validEncryptionKey =
            Single.just("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEc5uWBdVnLdvHczjMls2Drl5q1VV0V1lhp3mMFG9RLO+vDmC1uLtcmH412MLbOtW+ywjDaLUEDDD3h74DCXVcpw==")
        doReturn(validBindingKey).`when`(idNowManager).getIdentificationBase64EncodedPublicKey()
        doReturn(validEncryptionKey).`when`(idNowManager).getEncryptionBase64EncodedPublicKey()

        // When
        val testObserver = idNowManager.verifyReceiptJWSKeys(RECEIPT_JWS).test().await()

        // Then
        testObserver.assertNoErrors()
    }

    @Test
    fun `Receipt JWS is not verified when JWT is valid but keys dont match`() {
        // Given
        val validBindingKey =
            Single.just("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEndnCOOtefOnMzEtLjSUOjfGN3NnV60s0eTDlD/5IM4Cf6Y7fT8x2Kxlva9EGgNmP4Y2C6Ism5+8nuDQsyBl5gg==")
        val invalidEncryptionKey = Single.just("Key123")
        doReturn(validBindingKey).`when`(idNowManager).getIdentificationBase64EncodedPublicKey()
        doReturn(invalidEncryptionKey).`when`(idNowManager).getEncryptionBase64EncodedPublicKey()

        // When
        val testObserver = idNowManager.verifyReceiptJWSKeys(RECEIPT_JWS).test().await()

        // Then
        testObserver.assertError(IllegalArgumentException::class.java)
    }

    @Test
    fun `Deleting an ident successfully leads to clearance of shared ident locally`() {
        // Given
        idNowManager.persistLucaIdData(
            LucaIdData(
                revocationCode = "revocationCode",
                enrollmentToken = "enrollmentToken",
                verificationStatus = LucaIdData.VerificationStatus.SUCCESS,
            )
        ).blockingAwait()
        whenever(lucaIdEndpoints.deleteIdent("Token123")).thenReturn(Completable.complete())
        doReturn(Single.just("Token123")).`when`(attestationManager).getToken()

        // When
        val testObserver = idNowManager.unEnroll().test()
        triggerScheduler()

        // Then
        testObserver.await().assertNoErrors()
        idNowManager.getLucaIdDataIfAvailable().test().assertNoValues()
    }

    @Test
    fun `Example data can be decrypted`() {
        // Given
        val sampleIdentity = IdNowSamples.SuccessfulIdentityCheck()
        doReturn(Single.just(sampleIdentity.privateKeyAsEcPrivateKey())).`when`(cryptoManager)
            .getKeyPairPrivateKey("luca_id_encryption_key_pair") // TODO: refactor
        doReturn(Single.just("A769N5hWk/3H51OxiD03wt4fGangNtpJHR8z4z8Ek0CJ")).`when`(idNowManager).getIdentificationBase64EncodedPublicKey()
        val encryptedIdData = LucaIdData(sampleIdentity.responseData).encryptedIdData!!

        // When
        val identity = idNowManager.decryptLucaIdData(encryptedIdData).blockingGet()

        // Then
        Assert.assertEquals("RICHARD", identity.firstName)
        Assert.assertEquals("KOFER", identity.lastName)
        Assert.assertEquals(422402400000, identity.birthdayTimestamp)
    }

    fun `When verification polling is required, status is updated initially and after intervals`() {
        // Given
        idNowManager.persistEnrollmentStatusUpdatesRequired(true).blockingAwait()
        var updateCounter = 0
        doReturn(Completable.fromAction { updateCounter++ }).`when`(idNowManager).updateEnrollmentStatus()

        // When
        idNowManager.startUpdatingEnrollmentStatus().test()

        // Then
        rxSchedulersRule.testScheduler.triggerActions()
        assertEquals(1, updateCounter)
        rxSchedulersRule.testScheduler.advanceTimeBy(5, TimeUnit.MINUTES)
        assertEquals(2, updateCounter)
        rxSchedulersRule.testScheduler.advanceTimeBy(4, TimeUnit.MINUTES)
        assertEquals(2, updateCounter)
        rxSchedulersRule.testScheduler.advanceTimeBy(1, TimeUnit.MINUTES)
        assertEquals(3, updateCounter)
    }
}
