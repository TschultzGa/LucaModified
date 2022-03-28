package de.culture4life.luca.crypto

import java.security.interfaces.ECPublicKey

/**
 * Discrete Logarithm Integrated Encryption Scheme (ecies)
 */
data class EciesResult(
    var encryptedData: ByteArray,
    var iv: ByteArray,
    var mac: ByteArray,
    val ephemeralPublicKey: ECPublicKey
)
