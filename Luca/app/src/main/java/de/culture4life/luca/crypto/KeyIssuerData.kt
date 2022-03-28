package de.culture4life.luca.crypto

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.util.decodeFromBase64
import java.security.interfaces.ECPublicKey

data class KeyIssuerData(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("name")
    val name: String,

    @Expose
    @SerializedName("type")
    val type: String,

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
        get() {
            return AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()
        }

    var signedJwt: String? = null
}
