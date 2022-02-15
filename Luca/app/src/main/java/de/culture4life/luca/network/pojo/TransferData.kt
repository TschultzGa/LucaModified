package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

/**
 * Contains tracing secrets, data secrets and user IDs. Will be encrypted using the daily
 * keypair to be accessed by the health department only.
 *
 * @see [Tracing the Check-In History of an Infected Guest](https://luca-app.de/securityoverview/processes/tracing_access_to_history.html?highlight=transfer#accessing-the-infected-guest-s-tracing-secrets)
 */
data class TransferData(

    @SerializedName("u")
    val userDataWrappers: List<UserDataWrapper>

) {

    @SerializedName("v")
    val version: Int = 4

}

data class UserDataWrapper(

    @SerializedName("id")
    val id: String,

    @SerializedName("rts")
    val registrationTimestamp: Long,

    @SerializedName("ds")
    val dataSecret: String,

    @SerializedName("ts")
    val traceSecretWrappers: MutableList<TraceSecretWrapper>

)

data class TraceSecretWrapper(

    @SerializedName("ts")
    val timestamp: Long,

    @SerializedName("s")
    val traceSecret: String

)