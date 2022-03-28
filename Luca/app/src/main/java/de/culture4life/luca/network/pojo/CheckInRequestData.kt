package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class CheckInRequestData(

    @SerializedName("traceId")
    var traceId: String? = null,

    @SerializedName("scannerId")
    var scannerId: String? = null,

    @SerializedName("timestamp")
    var unixTimestamp: Long = 0,

    @SerializedName("data")
    var reEncryptedQrCodeData: String? = null,

    @SerializedName("iv")
    var iv: String? = null,

    @SerializedName("mac")
    var mac: String? = null,

    @SerializedName("publicKey")
    var scannerEphemeralPublicKey: String? = null,

    @SerializedName("authPublicKey")
    var guestEphemeralPublicKey: String? = null,

    @SerializedName("deviceType")
    var deviceType: Int = 0

)
