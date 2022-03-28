package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class LocationUrlsResponseData(
    @SerializedName("menu") val menu: String?,
    @SerializedName("schedule") val program: String?,
    @SerializedName("map") val map: String?,
    @SerializedName("website") val website: String?,
    @SerializedName("general") val general: String?
)
