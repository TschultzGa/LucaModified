package de.culture4life.luca.crypto

import android.util.Base64.NO_PADDING
import de.culture4life.luca.util.decodeFromBase58
import de.culture4life.luca.util.decodeFromBase64
import dgca.verifier.app.decoder.toHexString
import okhttp3.internal.toHexString
import java.lang.IllegalArgumentException

data class DecentralizedIdentifierKey(val input: String) {

    val decodedKey: ByteArray by lazy {
        require(varInt.toHexString() == 0x8024.toHexString())
        decodedData.copyOfRange(varInt.size, decodedData.size)
    }

    private val decodedData: ByteArray by lazy {
        when (encoding) {
            "base58btc" -> input.substring(9).decodeFromBase58()
            "base64" -> input.substring(9).decodeFromBase64(NO_PADDING)
            else -> throw IllegalArgumentException("Unsupported key encoding: $encoding")
        }
    }

    private val varInt: ByteArray by lazy {
        when (decodedData.copyOfRange(0, 2).toHexString()) {
            0xfd.toHexString() -> decodedData.copyOfRange(0, 4)
            0xfe.toHexString() -> decodedData.copyOfRange(0, 6)
            0xff.toHexString() -> decodedData.copyOfRange(0, 8)
            else -> decodedData.copyOfRange(0, 2) // This should be the case for us
        }
    }

    // https://github.com/multiformats/multibase/blob/master/multibase.csv
    private val encoding: String
        get() = when (input[8]) {
            'z' -> "base58btc" // base58 bitcoin
            'm' -> "base64" // 	rfc4648 no padding
            else -> throw IllegalArgumentException("Unsupported key encoding: $encoding")
        }
}
