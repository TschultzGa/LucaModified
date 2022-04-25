package de.culture4life.luca.attestation

import de.culture4life.luca.util.TimeUtil
import java.security.cert.Certificate

data class KeyAttestationResult(
    val nonce: ByteArray,
    val certificates: List<Certificate> = emptyList(),
    val timestamp: Long = TimeUtil.getCurrentMillis()
)
