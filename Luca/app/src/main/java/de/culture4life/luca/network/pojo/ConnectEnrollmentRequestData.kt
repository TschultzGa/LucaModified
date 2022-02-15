package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.crypto.EciesResult
import de.culture4life.luca.crypto.toCompressedBase64String
import de.culture4life.luca.util.encodeToBase64

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
    var referenceData: EciesData,

    @SerializedName("data")
    var fullData: EciesData,

    @SerializedName("signature")
    var signature: String? = null,

    @SerializedName("pow")
    var pow: PowSolutionRequestData,

    )

data class EciesData(

    @SerializedName("data")
    var encryptedData: String,

    @SerializedName("publicKey")
    var ephemeralPublicKey: String,

    @SerializedName("iv")
    var iv: String,

    @SerializedName("mac")
    var mac: String,

    ) {

    constructor(eciesResult: EciesResult) : this(
        encryptedData = eciesResult.encryptedData.encodeToBase64(),
        ephemeralPublicKey = eciesResult.ephemeralPublicKey.toCompressedBase64String(),
        iv = eciesResult.iv.encodeToBase64(),
        mac = eciesResult.mac.encodeToBase64()
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

    @SerializedName("ci")
    var criticalInfrastructure: Boolean? = null,

    @SerializedName("vg")
    var vulnerableGroup: Boolean? = null,

    @SerializedName("jb")
    var industry: String? = null,

    @SerializedName("em")
    var company: String? = null,

    @SerializedName("v")
    var version: Int = 1,

    )