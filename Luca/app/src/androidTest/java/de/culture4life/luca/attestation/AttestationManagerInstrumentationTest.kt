package de.culture4life.luca.attestation

import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.util.encodeToHex
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.spy
import timber.log.Timber

class AttestationManagerInstrumentationTest : LucaInstrumentationTest() {

    private lateinit var attestationManager: AttestationManager

    @Before
    fun setup() {
        attestationManager = spy(getInitializedManager(application.attestationManager))
    }

    @Test
    fun getToken_validRequest_noError() {
        attestationManager.getToken()
            .test()
            .await()
            .assertValue { it.isNotEmpty() }
    }

    @Test
    fun registerDevice_validRequest_noError() {
        attestationManager.registerDevice()
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun getKeyAttestationResult_validRequest_noError() {
        val nonce = "challenge".toByteArray()
        attestationManager.getKeyAttestationResult(nonce)
            .doOnSuccess { result ->
                Timber.d("Attestation result: $result")
                result.certificates.forEach { certificate ->
                    Timber.d("Encoded certificate: ${certificate.encoded.encodeToHex()}")
                }
            }
            .test()
            .await()
            .assertValue { it.nonce.contentEquals(nonce) }
    }

    @Test
    fun getSafetyNetAttestationResult_validRequest_noError() {
        val nonce = "challenge".toByteArray()
        attestationManager.getSafetyNetAttestationResult(nonce)
            .doOnSuccess { result ->
                Timber.d("Attestation result: $result")
            }
            .test()
            .await()
            .assertValue { it.nonce.contentEquals(nonce) }
    }
}
