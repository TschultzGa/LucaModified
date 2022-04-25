package de.culture4life.luca.network.endpoints;

import com.google.gson.JsonObject;

import java.util.List;

import de.culture4life.luca.network.pojo.ConnectEnrollmentRequestData;
import de.culture4life.luca.network.pojo.ConnectMessageReadRequestData;
import de.culture4life.luca.network.pojo.ConnectMessageRequestData;
import de.culture4life.luca.network.pojo.ConnectMessageResponseData;
import de.culture4life.luca.network.pojo.ConnectUnEnrollmentRequestData;
import de.culture4life.luca.network.pojo.DailyPublicKeyResponseData;
import de.culture4life.luca.network.pojo.HealthDepartment;
import de.culture4life.luca.network.pojo.KeyIssuerResponseData;
import de.culture4life.luca.network.pojo.PowChallengeRequestData;
import de.culture4life.luca.network.pojo.PowChallengeResponseData;
import de.culture4life.luca.network.pojo.RolloutRatioResponseData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface LucaEndpointsV4 {

    /*
        Keys
     */

    @GET("keys/daily/current")
    Single<DailyPublicKeyResponseData> getDailyPublicKey();

    @GET("keys/issuers/{issuerId}")
    Single<KeyIssuerResponseData> getKeyIssuer(@Path("issuerId") String issuerId);

    /*
        Notifications
     */

    @GET("notifications/config")
    Single<JsonObject> getNotificationConfig();

    @GET("notifications/traces")
    Single<ResponseBody> getNotifications();

    @GET("notifications/traces/{chunkId}")
    Single<ResponseBody> getNotifications(@Path("chunkId") String chunkId);

    /*
        EUDCC Signing Keys
     */

    @GET("trustList/DSC")
    Single<ResponseBody> getEudccSigningKeys();

    /*
        Proof of work
     */

    @POST("pow/request")
    @Headers("Content-Type: application/json")
    Single<PowChallengeResponseData> getPowChallenge(@Body PowChallengeRequestData data);

    /*
        luca connect
     */

    @POST("connect/contacts")
    @Headers("Content-Type: application/json")
    Single<JsonObject> enrollToLucaConnect(@Body ConnectEnrollmentRequestData data);

    @HTTP(method = "DELETE", path = "connect/contacts", hasBody = true)
    Completable unEnrollFromLucaConnect(@Body ConnectUnEnrollmentRequestData data);

    @POST("connect/messages/receive")
    Single<List<ConnectMessageResponseData>> getMessages(@Body ConnectMessageRequestData data);

    @POST("connect/messages/read")
    Completable markMessageAsRead(@Body ConnectMessageReadRequestData data);

    @GET("zipCodes/connect")
    Single<List<HealthDepartment>> getResponsibleHealthDepartment();

    /*
        Feature rollouts
     */

    @GET("features")
    Single<List<RolloutRatioResponseData>> getRolloutRatios();

}
