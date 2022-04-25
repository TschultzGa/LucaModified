package de.culture4life.luca.network.pojo.attestation

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class AttestationTokenRequestData(

    @Expose
    @SerializedName("deviceId")
    val deviceId: String,

    @Expose
    @SerializedName("nonce")
    val nonce: String,

    @Expose
    @SerializedName("signature")
    val signature: String

)
