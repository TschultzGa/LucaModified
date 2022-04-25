package de.culture4life.luca.attestation

import de.culture4life.luca.util.TimeUtil

data class SafetyNetAttestationResult(
    val nonce: ByteArray,
    val jws: String?,
    val timestamp: Long = TimeUtil.getCurrentMillis()
)
