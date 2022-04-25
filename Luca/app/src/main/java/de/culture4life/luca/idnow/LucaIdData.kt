package de.culture4life.luca.idnow

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.network.pojo.id.IdentStatusResponseData

data class LucaIdData(

    @Expose
    @SerializedName("revocationCode")
    val revocationCode: String,

    @Expose
    @SerializedName("enrollmentToken")
    val enrollmentToken: String?,

    @Expose
    @SerializedName("verificationStatus")
    val verificationStatus: VerificationStatus,

    @Expose
    @SerializedName("encryptedIdData")
    val encryptedIdData: EncryptedIdData? = null,

    @Expose
    @SerializedName("decryptedIdData")
    val decryptedIdData: DecryptedIdData? = null,

    @Expose
    @SerializedName("receiptJWS")
    val receiptJWS: String? = null
) {

    constructor(responseData: IdentStatusResponseData) : this(
        revocationCode = responseData.revocationCode,
        enrollmentToken = responseData.identId,
        verificationStatus = VerificationStatus.valueOf(responseData.state.name),
        encryptedIdData = responseData.data?.let { EncryptedIdData(responseData.data) },
        decryptedIdData = null,
        receiptJWS = responseData.receiptJWS
    )

    data class EncryptedIdData(
        @Expose
        @SerializedName("faceJwe")
        val faceJwe: String,

        @Expose
        @SerializedName("identityJwe")
        val identityJwe: String,

        @Expose
        @SerializedName("minimalIdentityJwe")
        val minimalIdentityJwe: String
    ) {
        constructor(data: IdentStatusResponseData.Data) : this(
            faceJwe = data.valueFace,
            identityJwe = data.valueIdentity,
            minimalIdentityJwe = data.valueMinimalIdentity
        )
    }

    data class DecryptedIdData(
        @Expose
        @SerializedName("firstName")
        val firstName: String,

        @Expose
        @SerializedName("familyName")
        val lastName: String,

        @Expose
        @SerializedName("validSinceTimestamp")
        val validSinceTimestamp: Long,

        @Expose
        @SerializedName("birthdayTimestamp")
        val birthdayTimestamp: Long,

        @Expose
        @SerializedName("image")
        val image: String
    )

    enum class VerificationStatus {
        @Expose
        @SerializedName("UNINITIALIZED")
        UNINITIALIZED,

        @Expose
        @SerializedName("QUEUED")
        QUEUED,

        @Expose
        @SerializedName("PENDING")
        PENDING,

        @Expose
        @SerializedName("FAILED")
        FAILED,

        @Expose
        @SerializedName("SUCCESS")
        SUCCESS
    }
}
