package de.culture4life.luca.network.pojo.id

import com.google.gson.annotations.SerializedName

data class IdentCreationResponseData(
    @SerializedName("waitForMs")
    val statusUpdateDelay: Long
)
