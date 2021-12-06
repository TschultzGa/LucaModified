package de.culture4life.luca.network.endpoints;

import com.google.gson.JsonObject;

import de.culture4life.luca.network.pojo.DailyPublicKeyResponseData;
import de.culture4life.luca.network.pojo.KeyIssuerResponseData;
import io.reactivex.rxjava3.core.Single;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
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

}
