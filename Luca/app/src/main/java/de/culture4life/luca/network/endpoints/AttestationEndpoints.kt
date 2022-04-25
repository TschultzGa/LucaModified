package de.culture4life.luca.network.endpoints

import de.culture4life.luca.network.pojo.attestation.*
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface AttestationEndpoints {
    @get:GET("nonce/request")
    val getNonce: Single<AttestationNonceResponseData>

    @POST("devices/android/register")
    @Headers("Content-Type: application/json")
    fun registerDevice(@Body data: AttestationRegistrationRequestData?): Single<AttestationRegistrationResponseData>

    @POST("devices/android/assert")
    @Headers("Content-Type: application/json")
    fun getAttestationToken(@Body data: AttestationTokenRequestData?): Single<AttestationTokenResponseData>
}
