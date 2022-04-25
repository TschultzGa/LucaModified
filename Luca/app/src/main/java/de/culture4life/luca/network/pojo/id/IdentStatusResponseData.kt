package de.culture4life.luca.network.pojo.id

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class IdentStatusResponseData(
    @SerializedName("state")
    @Expose
    val state: State,
    @SerializedName("revocationCode")
    @Expose
    val revocationCode: String,
    @SerializedName("identId")
    @Expose
    val identId: String? = null,
    @SerializedName("data")
    @Expose
    val data: Data? = null,
    @SerializedName("receiptJWS")
    @Expose
    val receiptJWS: String? = null
) {

    data class Data(
        @Expose
        @SerializedName("valueFace")
        val valueFace: String,
        @Expose
        @SerializedName("valueIdentity")
        val valueIdentity: String,
        @Expose
        @SerializedName("valueMinimalIdentity")
        val valueMinimalIdentity: String
    )

    enum class State {
        @SerializedName("UNINITIALIZED")
        UNINITIALIZED,

        @SerializedName("QUEUED")
        QUEUED,

        @SerializedName("PENDING")
        PENDING,

        @SerializedName("FAILED")
        FAILED,

        @SerializedName("SUCCESS")
        SUCCESS
    }
}
