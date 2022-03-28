package de.culture4life.luca.registration

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

data class RegistrationArchive(

    @Expose
    @SerializedName("entries")
    var entries: List<RegistrationData> = ArrayList()

) : ArchivedData<RegistrationData> {

    override fun getData(): List<RegistrationData> {
        return entries
    }

    override fun setData(data: List<RegistrationData>) {
        entries = data
    }
}
