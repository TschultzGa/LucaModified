package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class RolloutRatioResponseData(

    @SerializedName("name")
    var id: String,

    @SerializedName("percentage")
    var rolloutRatio: Float

)
