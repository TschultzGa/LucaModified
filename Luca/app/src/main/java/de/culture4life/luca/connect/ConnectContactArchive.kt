package de.culture4life.luca.connect

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ConnectContactArchive(

    @Expose
    @SerializedName("entries")
    val entries: List<Entry> = ArrayList()

) {

    data class Entry(

        @Expose
        @SerializedName("contactId")
        val contactId: String,

        @Expose
        @SerializedName("timestamp")
        val timestamp: Long

    )

}

