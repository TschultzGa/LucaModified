package de.culture4life.luca.health

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.AsymmetricCipherProvider
import de.culture4life.luca.network.pojo.HealthDepartment
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.decodeFromBase64
import de.culture4life.luca.util.parseJwt
import java.security.interfaces.ECPublicKey

data class ResponsibleHealthDepartment(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("name")
    val name: String,

    @Expose
    @SerializedName("publicHDEKP")
    val publicHDEKP: String,

    @Expose
    @SerializedName("publicHDSKP")
    val publicHDSKP: String,

    @Expose
    @SerializedName("postalCode")
    val postalCode: String,

    ) {

    @Expose
    @SerializedName("updateTimestamp")
    var updateTimestamp: Long = 0

    val encryptionPublicKey: ECPublicKey
        get() {
            return AsymmetricCipherProvider.decodePublicKey(publicHDEKP.decodeFromBase64()).blockingGet()
        }

    val signingPublicKey: ECPublicKey
        get() {
            return AsymmetricCipherProvider.decodePublicKey(publicHDSKP.decodeFromBase64()).blockingGet()
        }

    constructor(healthDepartment: HealthDepartment, postalCode: String) : this(
        id = healthDepartment.id,
        name = healthDepartment.name,
        publicHDEKP = getKeyFromJwt(healthDepartment.encryptionKeyJwt),
        publicHDSKP = getKeyFromJwt(healthDepartment.signingKeyJwt),
        postalCode = postalCode
    ) {
        updateTimestamp = TimeUtil.getCurrentMillis()
    }

    companion object {
        private fun getKeyFromJwt(signedJwt: String): String {
            val claims = signedJwt.parseJwt().body
            return claims["key"] as String
        }
    }

}