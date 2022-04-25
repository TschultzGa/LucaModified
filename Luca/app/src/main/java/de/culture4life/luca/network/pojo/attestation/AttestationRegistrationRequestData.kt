package de.culture4life.luca.network.pojo.attestation

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class AttestationRegistrationRequestData(

    @Expose
    @SerializedName("nonce")
    val baseNonce: String,

    @Expose
    @SerializedName("keyAttestationPublicKey")
    val keyAttestationPublicKey: String,

    @Expose
    @SerializedName("keyAttestationCertificates")
    val keyAttestationCertificates: List<String>,

    @Expose
    @SerializedName("safetyNetAttestationJws")
    val safetyNetAttestationJws: String

)
