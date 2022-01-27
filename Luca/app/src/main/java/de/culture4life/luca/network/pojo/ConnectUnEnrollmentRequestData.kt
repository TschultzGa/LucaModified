package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName

data class ConnectUnEnrollmentRequestData(

    @SerializedName("contactId")
    var contactId: String,

    @SerializedName("timestamp")
    var timestamp: Long,

    @SerializedName("signature")
    var signature: String? = null,

    )