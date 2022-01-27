package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.DliesResult
import de.culture4life.luca.crypto.encodeToBase64
import de.culture4life.luca.crypto.toCompressedBase64String
import java.security.interfaces.ECPublicKey

data class ConnectEnrollmentRequestData(

    @SerializedName("authPublicKey")
    var authPublicKey: String,

    @SerializedName("departmentId")
    var departmentId: String,

    @SerializedName("namePrefix")
    var namePrefix: String,

    @SerializedName("phonePrefix")
    var phonePrefix: String,

    @SerializedName("reference")
    var referenceData: DliesData,

    @SerializedName("data")
    var fullData: DliesData,

    @SerializedName("signature")
    var signature: String? = null,

    @SerializedName("pow")
    var pow: PowSolutionRequestData,

    )

data class DliesData(

    @SerializedName("data")
    var encryptedData: String,

    @SerializedName("publicKey")
    var ephemeralPublicKey: String,

    @SerializedName("iv")
    var iv: String,

    @SerializedName("mac")
    var mac: String,

    ) {

    constructor(dliesResult: DliesResult) : this(
        encryptedData = dliesResult.encryptedData.encodeToBase64(),
        ephemeralPublicKey = (dliesResult.ephemeralPublicKey as ECPublicKey).toCompressedBase64String(),
        iv = dliesResult.iv.encodeToBase64(),
        mac = dliesResult.mac.encodeToBase64()
    )

}

data class ConnectContactData(

    @SerializedName("cd")
    var wrappedContactData: ContactData,

    @SerializedName("ct")
    var covidCertificates: List<String>,

    @SerializedName("ns")
    var notificationSeed: String,

    @SerializedName("ep")
    var encryptionPublicKey: String,

    @SerializedName("sp")
    var signingPublicKey: String,

    @SerializedName("dt")
    var deviceType: Int = 0,

    @SerializedName("v")
    var version: Int = 1,

    )