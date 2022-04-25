package de.culture4life.luca.attestation

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.util.encodeToBase64
import org.junit.Test
import org.mockito.Mockito
import timber.log.Timber

class AttestationManagerTest : LucaUnitTest() {

    private val attestationManager = Mockito.spy(getInitializedManager(application.attestationManager))

    @Test
    fun `Created registration request data contains base nonce`() {
        val testObserver = attestationManager.createRegistrationRequestData(BASE_NONCE)
            .doOnSuccess { Timber.d("Registration request data: $it") }
            .test()
        triggerScheduler()
        testObserver.assertValue { it.baseNonce == String(BASE_NONCE) }
    }

    @Test
    fun `Key attestation nonce generated as expected`() {
        attestationManager.generateKeyAttestationNonce(BASE_NONCE)
            .map { it.encodeToBase64() }
            .test()
            .assertValue("Ta1Q2CWFzG4D4jTdTCyjR4pqz4r1dZkZVhJDb0hTC90=")
    }

    @Test
    fun `SafetyNet nonce generated as expected`() {
        attestationManager.generateSafetyNetNonce(BASE_NONCE)
            .map { it.encodeToBase64() }
            .test()
            .assertValue("xbEiKXob0FpFXU7xvvm2VuM55Nao17aswp4czFedG9A=")
    }

    companion object {
        val BASE_NONCE = "YWr0DmBHu++0IMt3hO8R7PPTGbGTDYFS3ddFsa1QyKY=".toByteArray()
    }
}
