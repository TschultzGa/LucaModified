package de.culture4life.luca.idnow

import com.google.gson.annotations.SerializedName

data class ReceiptJWTBody(
    @SerializedName("input_data")
    val inputData: InputData,
    @SerializedName("autoIdentId")
    val enrollmentToken: String,
    @SerializedName("transactionNumber")
    val transactionNumber: String
) {
    data class InputData(
        @SerializedName("bindingKey")
        val identificationKey: String,
        @SerializedName("encryptionKey")
        val encryptionKey: String,
    )
}
