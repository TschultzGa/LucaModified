package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.KeyIssuerData
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.encodeToHex
import de.culture4life.luca.util.parseJwt
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class KeyIssuerResponseData(

    @SerializedName("uuid")
    val id: String,

    @SerializedName("publicCertificate")
    val encodedCertificate: String,

    @SerializedName("signedPublicHDEKP")
    val encryptionKeyJwt: String,

    @SerializedName("signedPublicHDSKP")
    val signingKeyJwt: String

) {

    val certificate: X509Certificate
        get() {
            val stream = ByteArrayInputStream(encodedCertificate.toByteArray())
            return CertificateFactory.getInstance("X509").generateCertificate(stream) as X509Certificate
        }

    val encryptionKeyData: KeyIssuerData
        get() {
            val data = parseKeyJwt(encryptionKeyJwt)
            require(data.type == "publicHDEKP")
            return data
        }

    val signingKeyData: KeyIssuerData
        get() {
            val data = parseKeyJwt(signingKeyJwt)
            require(data.type == "publicHDSKP")
            return data
        }

    /**
     * Example header and payload:
     *
     * ```json
     * {
     *   "alg": "RS512",
     *   "typ": "JWT"
     * }
     * {
     *   "sub": "d229e28b-f881-4945-b0d8-09a413b04e00",
     *   "iss": "6766ea7e43226228d5a8ecb095b6e43f265a826d",
     *   "name": "Gesundheitsamt Dev",
     *   "key": "BMWmMRLSidXLrUwFQr9VwYB+3zrAmnUxOLTX9y9lNGmGO31SrBXAI9wNfIa75bq9V5TIkUUwmq8MN4oGD0oyGnI=",
     *   "type": "publicHDSKP",
     *   "iat": 1638451201
     * }
     * ```
     */
    private fun parseKeyJwt(signedJwt: String): KeyIssuerData {
        val jwt = signedJwt.parseJwt()
        require(jwt.header["alg"] == "RS512")
        val certHash = DigestUtils.sha1(certificate.encoded).encodeToHex()
        require(jwt.body.issuer == certHash)

        return KeyIssuerData(
            id = jwt.body["sub"] as String,
            name = jwt.body["name"] as String,
            type = jwt.body["type"] as String,
            creationTimestamp = TimeUtil.convertFromUnixTimestamp((jwt.body["iat"] as Int).toLong()).blockingGet(),
            encodedPublicKey = jwt.body["key"] as String,
            issuerId = jwt.body["iss"] as String
        ).apply { this.signedJwt = signedJwt }
    }

}