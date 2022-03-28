package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.DailyPublicKeyData
import de.culture4life.luca.crypto.DailyPublicKeyData.Companion.EXPIRATION_TIMESTAMP_NONE
import de.culture4life.luca.util.fromUnixTimestamp
import de.culture4life.luca.util.parseJwt

data class DailyPublicKeyResponseData(

    @SerializedName("signedPublicDailyKey")
    val dailyKeyJwt: String

) {

    val dailyPublicKeyData: DailyPublicKeyData
        get() = parseDailyKeyJwt()

    /**
     * Example header and payload:
     *
     * ```json
     * {
     *   "alg": "ES256",
     *   "typ": "JWT"
     * }
     * {
     *   "type": "publicDailyKey",
     *   "iss": "d229e28b-f881-4945-b0d8-09a413b04e00",
     *   "keyId": 23,
     *   "key": "BPdA/JeXeZSiKWW01pQI+HAqGRmWcveMsFnRebtpQIHUOfMjVJ1kWrfTsdBbAT6oGl0nc+Ae6TX2FvfM97p7x24=",
     *   "iat": 1647334045,
     *   "exp": 1649753245
     * }
     * ```
     */
    private fun parseDailyKeyJwt(): DailyPublicKeyData {
        val jwt = dailyKeyJwt.parseJwt()
        require(jwt.header["alg"] == "ES256")
        require(jwt.body["type"] as String == "publicDailyKey")

        return DailyPublicKeyData(
            id = jwt.body["keyId"] as Int,
            creationTimestamp = (jwt.body["iat"] as Int).toLong().fromUnixTimestamp(),
            expirationTimestamp = (jwt.body["exp"] as? Int).run { this?.toLong()?.fromUnixTimestamp() } ?: EXPIRATION_TIMESTAMP_NONE,
            encodedPublicKey = jwt.body["key"] as String,
            issuerId = jwt.body["iss"] as String
        ).apply {
            this.signedJwt = dailyKeyJwt
        }
    }
}
