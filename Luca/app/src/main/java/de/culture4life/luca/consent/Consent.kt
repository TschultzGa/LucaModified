package de.culture4life.luca.consent

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Consent(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("approved")
    val approved: Boolean = false,

    @Expose
    @SerializedName("lastDisplayTimestamp")
    val lastDisplayTimestamp: Long = 0

)
