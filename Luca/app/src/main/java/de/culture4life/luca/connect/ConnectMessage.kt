package de.culture4life.luca.connect

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ConnectMessage(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("title")
    val title: String,

    @Expose
    @SerializedName("content")
    val content: String,

    @Expose
    @SerializedName("timestamp")
    val timestamp: Long,

    @Expose
    @SerializedName("read")
    var read: Boolean

)