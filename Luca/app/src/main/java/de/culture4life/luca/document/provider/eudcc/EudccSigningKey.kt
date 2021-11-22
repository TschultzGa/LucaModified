package de.culture4life.luca.document.provider.eudcc

data class EudccSigningKey(
    val certificateType: String,
    val country: String,
    val kid: String,
    val rawData: String,
    val signature: String,
    val thumbprint: String,
    val timestamp: String
)
