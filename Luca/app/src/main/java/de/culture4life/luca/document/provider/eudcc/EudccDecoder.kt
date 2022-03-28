package de.culture4life.luca.document.provider.eudcc

import COSE.HeaderKeys
import android.text.TextUtils
import com.upokecenter.cbor.CBORObject
import dgca.verifier.app.decoder.CertificateDecodingResult
import dgca.verifier.app.decoder.DefaultCertificateDecoder
import dgca.verifier.app.decoder.base45.Base45Decoder
import dgca.verifier.app.decoder.cwt.CwtHeaderKeys
import dgca.verifier.app.decoder.model.CoseData
import java.time.Instant
import java.util.zip.InflaterInputStream

class EudccDecoder(private val base45Decoder: Base45Decoder) {

    private val decoder = DefaultCertificateDecoder(base45Decoder)

    fun decodeCertificate(encodedCertificate: String): CertificateDecodingResult {
        val certificate = decoder.decodeCertificate(encodedCertificate)
        val coseData = decodeCoseData(encodedCertificate)
        sanityCheckCertificate(coseData.cbor)
        return certificate
    }

    private fun sanityCheckCertificate(cbor: ByteArray) {
        val map = CBORObject.DecodeFromBytes(cbor)

        val issuingCountry = map[CwtHeaderKeys.ISSUING_COUNTRY.asCBOR()].AsString()
        if (TextUtils.isEmpty(issuingCountry)) throw IllegalArgumentException("Issuing country not correct: $issuingCountry")

        val issuedAt = Instant.ofEpochSecond(map[CwtHeaderKeys.ISSUED_AT.asCBOR()].AsInt64())
        if (issuedAt.isAfter(Instant.now())) throw IllegalArgumentException("IssuedAt not correct: $issuedAt")

        val expirationTime = Instant.ofEpochSecond(map[CwtHeaderKeys.EXPIRATION.asCBOR()].AsInt64())
        if (expirationTime.isBefore(Instant.now())) throw IllegalArgumentException("Expiration not correct: $expirationTime")
    }

    fun getEncodedCoseData(encodedCertificate: String): ByteArray {
        val encodedCertificateWithoutPrefix: String = if (encodedCertificate.startsWith(DefaultCertificateDecoder.PREFIX)) {
            encodedCertificate.drop(DefaultCertificateDecoder.PREFIX.length)
        } else {
            encodedCertificate
        }
        val base45Decoded = base45Decoder.decode(encodedCertificateWithoutPrefix)
        return decompressBase45DecodedData(base45Decoded)
    }

    fun decodeCoseData(encodedCertificate: String): CoseData {
        val encodedCoseData = getEncodedCoseData(encodedCertificate)
        return decodeCoseData(encodedCoseData)
    }

    companion object {

        @JvmStatic
        fun decompressBase45DecodedData(compressedData: ByteArray): ByteArray {
            // ZLIB magic headers
            val hasExpectedHeaders = compressedData.size >= 2 &&
                compressedData[0] == 0x78.toByte() &&
                (
                    compressedData[1] == 0x01.toByte() || // Level 1
                        compressedData[1] == 0x5E.toByte() || // Level 2 - 5
                        compressedData[1] == 0x9C.toByte() || // Level 6
                        compressedData[1] == 0xDA.toByte()
                    )

            return if (hasExpectedHeaders) {
                InflaterInputStream(compressedData.inputStream()).readBytes()
            } else {
                compressedData
            }
        }

        @JvmStatic
        fun decodeCoseData(encodedData: ByteArray): CoseData {
            val messageObject = CBORObject.DecodeFromBytes(encodedData)
            val content = messageObject[2].GetByteString()
            val protectedRgb = messageObject[0].GetByteString()
            val unprotectedRgb = messageObject[1]
            val key = HeaderKeys.KID.AsCBOR()

            if (!CBORObject.DecodeFromBytes(protectedRgb).keys.contains(key)) {
                val unprotectedObject = unprotectedRgb.get(key).GetByteString()
                return CoseData(content, unprotectedObject)
            }
            val objProtected = CBORObject.DecodeFromBytes(protectedRgb).get(key).GetByteString()
            return CoseData(content, objProtected)
        }
    }
}
