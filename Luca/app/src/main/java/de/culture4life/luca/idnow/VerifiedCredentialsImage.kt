package de.culture4life.luca.idnow

import com.google.gson.annotations.SerializedName

data class VerifiedCredentialsImage(@SerializedName("credentialSubject") val credentialSubject: CredentialSubject,) {
    data class CredentialSubject(@SerializedName("image") val image: String)
}
