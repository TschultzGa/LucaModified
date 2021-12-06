package de.culture4life.luca.crypto

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.security.interfaces.ECPublicKey

data class DailyPublicKeyData(

    @Expose
    @SerializedName("id")
    val id: Int,

    @Expose
    @SerializedName("creationTimestamp")
    val creationTimestamp: Long,

    @Expose
    @SerializedName("encodedPublicKey")
    val encodedPublicKey: String,

    @Expose
    @SerializedName("issuerId")
    val issuerId: String

) {

    val publicKey: ECPublicKey
        get() = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()

    var signedJwt: String? = null

}
