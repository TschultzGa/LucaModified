package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

class ConnectMessageResponseData(

    @SerializedName("messageId")
    val id: String,

    @SerializedName("data")
    val data: String,

    @SerializedName("iv")
    val iv: String,

    @SerializedName("mac")
    val mac: String,

    @SerializedName("createdAt")
    val timestamp: Long

)