package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.KeyIssuerData
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.parseJwt
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

    private fun parseKeyJwt(signedJwt: String): KeyIssuerData {
        val claims = signedJwt.parseJwt().body
        return KeyIssuerData(
            id = claims["sub"] as String,
            name = claims["name"] as String,
            type = claims["type"] as String,
            creationTimestamp = TimeUtil.convertFromUnixTimestamp((claims["iat"] as Int).toLong()).blockingGet(),
            encodedPublicKey = claims["key"] as String,
            issuerId = claims["iss"] as String
        ).apply {
            this.signedJwt = signedJwt
        }
    }

}