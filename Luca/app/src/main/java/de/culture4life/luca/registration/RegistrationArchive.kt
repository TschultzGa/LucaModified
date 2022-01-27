package de.culture4life.luca.registration

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class RegistrationArchive(

    @Expose
    @SerializedName("entries")
    val entries: List<RegistrationData>

)