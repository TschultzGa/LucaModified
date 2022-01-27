package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

class ConnectMessageReadRequestData(

    @SerializedName("messageId")
    val id: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("signature")
    val signature: String

)