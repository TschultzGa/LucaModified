package de.culture4life.luca.network.pojo.attestation

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class AttestationTokenResponseData(

    @Expose
    @SerializedName("jwt")
    val jwt: String

)
