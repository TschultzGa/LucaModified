package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class LocationResponseData(

    @SerializedName("locationId")
    val locationId: String? = null,

    @SerializedName("locationName")
    val areaName: String? = null,

    @SerializedName("groupName")
    val groupName: String? = null,

    @SerializedName("lat")
    val latitude: Double = 0.0,

    @SerializedName("lng")
    val longitude: Double = 0.0,

    @SerializedName("radius")
    val radius: Long = 0,

    @SerializedName("isPrivate")
    val isPrivate: Boolean = false,

    @SerializedName("isContactDataMandatory")
    val isContactDataMandatory: Boolean = true,

    @SerializedName("entryPolicy")
    val entryPolicy: EntryPolicy? = null,

    @SerializedName("averageCheckinTime")
    val averageCheckInDuration: Long = 0
) : Serializable {
    enum class EntryPolicy {
        POLICY_2G, POLICY_3G
    }
}
