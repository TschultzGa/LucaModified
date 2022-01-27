package de.culture4life.luca.crypto

import java.security.PublicKey

/**
 * Discrete Logarithm Integrated Encryption Scheme (DLIES)
 */
data class DliesResult(
    var encryptedData: ByteArray,
    var iv: ByteArray,
    var mac: ByteArray,
    val ephemeralPublicKey: PublicKey
)