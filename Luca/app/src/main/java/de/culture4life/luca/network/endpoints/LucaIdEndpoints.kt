package de.culture4life.luca.network.endpoints

import de.culture4life.luca.network.pojo.id.IdentCreationRequestData
import de.culture4life.luca.network.pojo.id.IdentCreationResponseData
import de.culture4life.luca.network.pojo.id.IdentStatusResponseData
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import retrofit2.http.*

interface LucaIdEndpoints {
    @POST("ident")
    fun createIdent(@Header("X-Auth") attestationToken: String, @Body data: IdentCreationRequestData): Single<IdentCreationResponseData>

    @GET("ident")
    fun getIdentStatus(@Header("X-Auth") attestationToken: String): Single<IdentStatusResponseData>

    @DELETE("ident/data")
    fun deleteIdentData(@Header("X-Auth") attestationToken: String): Completable

    @DELETE("ident")
    fun deleteIdent(@Header("X-Auth") attestationToken: String): Completable
}
