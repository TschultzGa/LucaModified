package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class TraceDeletionRequestData(

    @SerializedName("traceId")
    val traceId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("signature")
    val signature: String

)