package de.culture4life.luca.checkin

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * Check-In data containing trace id enabling health departments to contact guests in case of an
 * infection.
 *
 * @see [Security
 * Overview: Assets](https://luca-app.de/securityoverview/properties/assets.html.term-Check-In)
 */
data class CheckInData(

    @SerializedName("traceId")
    @Expose
    var traceId: String? = null,

    @SerializedName("locationId")
    @Expose
    var locationId: UUID? = null,

    @SerializedName("locationName")
    @Expose
    var locationAreaName: String? = null,

    @SerializedName("locationGroupName")
    @Expose
    var locationGroupName: String? = null,

    @SerializedName("timestamp")
    @Expose
    var timestamp: Long = 0,

    @SerializedName("latitude")
    @Expose
    var latitude: Double = 0.0,

    @SerializedName("longitude")
    @Expose
    var longitude: Double = 0.0,

    @SerializedName("radius")
    @Expose
    var radius: Long = 0,

    @SerializedName("minimumDuration")
    @Expose
    var minimumDuration: Long = 0,

    @SerializedName("averageCheckInDuration")
    @Expose
    var averageCheckInDuration: Long = 0,

    @SerializedName("isPrivateMeeting")
    @Expose
    var isPrivateMeeting: Boolean = false,

    @SerializedName("isContactDataMandatory")
    @Expose
    var isContactDataMandatory: Boolean = false

) {

    val locationDisplayName: String?
        get() = if (locationGroupName != null && locationAreaName != null) {
            "$locationGroupName - $locationAreaName"
        } else if (locationGroupName != null) {
            locationGroupName
        } else if (locationAreaName != null) {
            locationAreaName
        } else {
            null
        }

    fun hasLocation(): Boolean {
        return latitude != 0.0 && longitude != 0.0
    }

    fun hasLocationRestriction(): Boolean {
        return hasLocation() && radius > 0
    }

    fun hasDurationRestriction(): Boolean {
        return minimumDuration > 0
    }

}