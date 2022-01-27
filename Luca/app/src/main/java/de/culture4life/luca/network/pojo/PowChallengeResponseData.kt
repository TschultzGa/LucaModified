package de.culture4life.luca.network.pojo

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.pow.PowChallenge
import de.culture4life.luca.util.TimeUtil
import java.math.BigInteger

data class PowChallengeResponseData(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("t")
    val t: String,

    @Expose
    @SerializedName("n")
    val n: String,

    @Expose
    @SerializedName("expiresAt")
    val expirationTimestamp: Long

) {

    val powChallenge: PowChallenge
        get() = PowChallenge(
            id = id,
            t = BigInteger(t, 10).toInt(),
            n = BigInteger(n, 10),
            expirationTimestamp = TimeUtil.convertFromUnixTimestamp(expirationTimestamp).blockingGet()
        )

}