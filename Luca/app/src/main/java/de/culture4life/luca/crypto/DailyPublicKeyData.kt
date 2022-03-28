package de.culture4life.luca.crypto

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.util.decodeFromBase64
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit

data class DailyPublicKeyData(

    @Expose
    @SerializedName("id")
    val id: Int,

    @Expose
    @SerializedName("creationTimestamp")
    val creationTimestamp: Long,

    @Expose
    @SerializedName("expirationTimestamp")
    val expirationTimestamp: Long = EXPIRATION_TIMESTAMP_NONE,

    @Expose
    @SerializedName("encodedPublicKey")
    val encodedPublicKey: String,

    @Expose
    @SerializedName("issuerId")
    val issuerId: String

) {

    val publicKey: ECPublicKey
        get() = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()

    val expirationTimestampOrDefault: Long
        get() = if (expirationTimestamp != EXPIRATION_TIMESTAMP_NONE) expirationTimestamp else creationTimestamp + EXPIRATION_DURATION_DEFAULT

    var signedJwt: String? = null

    companion object {
        const val EXPIRATION_TIMESTAMP_NONE = 0L
        val EXPIRATION_DURATION_DEFAULT = TimeUnit.DAYS.toMillis(7)
    }
}
