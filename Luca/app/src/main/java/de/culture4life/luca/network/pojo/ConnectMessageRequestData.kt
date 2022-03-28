package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

class ConnectMessageRequestData(

    @SerializedName("messageIds")
    val ids: List<String>

)
