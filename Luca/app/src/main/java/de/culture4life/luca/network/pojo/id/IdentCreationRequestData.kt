package de.culture4life.luca.network.pojo.id

import com.google.gson.annotations.SerializedName

data class IdentCreationRequestData(
    @SerializedName("encPublicKey")
    val encryptionPublicKey: String,
    @SerializedName("idPublicKey")
    val identificationPublicKey: String,
    @SerializedName("idPublicKeyAttestationNonce")
    val identificationKeyNonce: String,
    @SerializedName("idPublicKeyAttestationCertificates")
    val identificationKeyCertificates: List<String>,
    @SerializedName("nonce")
    val attestationKeyNonce: String,
    @SerializedName("signature")
    val attestationKeySignature: String,
)
