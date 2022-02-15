package de.culture4life.luca.connect

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

data class ConnectContactArchive(

    @Expose
    @SerializedName("entries")
    var entries: List<Entry> = ArrayList()

) : ArchivedData<ConnectContactArchive.Entry> {

    data class Entry(

        @Expose
        @SerializedName("contactId")
        val contactId: String,

        @Expose
        @SerializedName("timestamp")
        val timestamp: Long

    )

    override fun getData(): List<Entry> {
        return entries
    }

    override fun setData(data: List<Entry>) {
        entries = data
    }

}

