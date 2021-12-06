package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.DailyPublicKeyData
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.parseJwt

data class DailyPublicKeyResponseData(

    @SerializedName("signedPublicDailyKey")
    val dailyKeyJwt: String

) {

    val dailyPublicKeyData: DailyPublicKeyData
        get() = parseDailyKeyJwt()


    private fun parseDailyKeyJwt(): DailyPublicKeyData {
        val claims = dailyKeyJwt.parseJwt().body
        require(claims["type"] as String == "publicDailyKey")
        return DailyPublicKeyData(
            id = claims["keyId"] as Int,
            creationTimestamp = TimeUtil.convertFromUnixTimestamp((claims["iat"] as Int).toLong()).blockingGet(),
            encodedPublicKey = claims["key"] as String,
            issuerId = claims["iss"] as String
        ).apply {
            this.signedJwt = dailyKeyJwt
        }
    }

}