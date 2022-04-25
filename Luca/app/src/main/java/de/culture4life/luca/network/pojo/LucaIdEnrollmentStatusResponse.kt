package de.culture4life.luca.network.pojo

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class LucaIdEnrollmentStatusResponse(

    @SerializedName("status")
    @Expose
    val status: String,

    @SerializedName("token")
    @Expose
    val token: String
)
