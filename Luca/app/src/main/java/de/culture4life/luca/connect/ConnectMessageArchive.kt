package de.culture4life.luca.connect

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ConnectMessageArchive(

    @Expose
    @SerializedName("entries")
    val entries: List<ConnectMessage> = ArrayList()

)
