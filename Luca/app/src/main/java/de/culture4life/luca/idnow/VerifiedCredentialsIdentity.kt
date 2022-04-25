package de.culture4life.luca.idnow

import com.google.gson.annotations.SerializedName

data class VerifiedCredentialsIdentity(@SerializedName("credentialSubject") val credentialSubject: CredentialSubject) {
    data class CredentialSubject(
        @SerializedName("familyName") val familyName: String,
        @SerializedName("givenName") val givenName: String,
        @SerializedName("birthDate") val birthDate: String,
    )
}
