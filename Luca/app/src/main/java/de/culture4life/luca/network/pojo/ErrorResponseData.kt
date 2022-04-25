package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class ErrorResponseData(
    @SerializedName("statusCode")
    val code: Int,
    @SerializedName("error")
    val name: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("stack")
    val stacktrace: String
)
