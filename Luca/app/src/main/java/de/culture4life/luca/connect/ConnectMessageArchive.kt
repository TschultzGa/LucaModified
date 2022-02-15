package de.culture4life.luca.connect

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

data class ConnectMessageArchive(

    @Expose
    @SerializedName("entries")
    var entries: List<ConnectMessage> = ArrayList()

) : ArchivedData<ConnectMessage> {
    override fun getData(): List<ConnectMessage> {
        return entries
    }

    override fun setData(data: List<ConnectMessage>) {
        entries = data
    }

}
