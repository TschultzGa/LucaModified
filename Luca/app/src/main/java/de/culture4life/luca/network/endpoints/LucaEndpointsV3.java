package de.culture4life.luca.network.endpoints;

import com.google.gson.JsonObject;

import java.util.List;

import de.culture4life.luca.meeting.MeetingCreationResponse;
import de.culture4life.luca.network.pojo.AdditionalCheckInPropertiesRequestData;
import de.culture4life.luca.network.pojo.CheckInRequestData;
import de.culture4life.luca.network.pojo.CheckOutRequestData;
import de.culture4life.luca.network.pojo.DataTransferRequestData;
import de.culture4life.luca.network.pojo.DocumentProviderDataList;
import de.culture4life.luca.network.pojo.LocationResponseData;
import de.culture4life.luca.network.pojo.LocationUrlsResponseData;
import de.culture4life.luca.network.pojo.TraceData;
import de.culture4life.luca.network.pojo.TraceDeletionRequestData;
import de.culture4life.luca.network.pojo.TracesResponseData;
import de.culture4life.luca.network.pojo.UserDeletionRequestData;
import de.culture4life.luca.network.pojo.UserRegistrationRequestData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface LucaEndpointsV3 {

    /*
        Locations
     */

    @GET("locations/{locationId}")
    Single<LocationResponseData> getLocation(@Path("locationId") String locationId);

    @GET("locations/{locationId}/urls")
    Single<LocationUrlsResponseData> getLocationUrls(@Path("locationId") String locationId);

    @POST("locations/private")
    Single<MeetingCreationResponse> createPrivateLocation(@Body JsonObject message);

    @GET("locations/traces/{accessId}")
    Single<List<TracesResponseData>> fetchGuestList(@Path("accessId") String accessId);

    @DELETE("locations/{accessId}")
    Completable closePrivateLocation(@Path("accessId") String accessId);

    /*
        Users
     */

    @POST("users")
    @Headers("Content-Type: application/json")
    Single<JsonObject> registerUser(@Body UserRegistrationRequestData data);

    @PATCH("users/{userId}")
    @Headers("Content-Type: application/json")
    Completable updateUser(@Path("userId") String userId, @Body UserRegistrationRequestData data);

    @HTTP(method = "DELETE", path = "users/{userId}", hasBody = true)
    @Headers("Content-Type: application/json")
    Completable deleteUser(@Path("userId") String userId, @Body UserDeletionRequestData data);

    /*
        Traces
     */

    @GET("traces/{traceId}")
    Single<TraceData> getTrace(@Path("traceId") String hexEncodedTraceId);

    @POST("traces/bulk")
    @Headers("Content-Type: application/json")
    Single<List<TraceData>> getTraces(@Body JsonObject traceIds);

    @POST("traces/checkin")
    @Headers("Content-Type: application/json")
    Completable checkIn(@Body CheckInRequestData data);

    @POST("traces/checkout")
    @Headers("Content-Type: application/json")
    Completable checkOut(@Body CheckOutRequestData data);

    @POST("traces/additionalData")
    @Headers("Content-Type: application/json")
    Completable addAdditionalCheckInProperties(@Body AdditionalCheckInPropertiesRequestData data);

    @HTTP(method = "DELETE", path = "traces", hasBody = true)
    @Headers("Content-Type: application/json")
    Completable deleteTrace(@Body TraceDeletionRequestData data);

    /*
        Scanners
     */

    @GET("scanners/{scannerId}")
    Single<JsonObject> getScanner(@Path("scannerId") String scannerId);

    /*
        Health Departments
     */

    @GET("healthDepartments/{healthDepartmentId}")
    Single<JsonObject> getHealthDepartment(@Path("healthDepartmentId") String healthDepartmentId);

    /*
        Transfers
     */

    @POST("userTransfers")
    @Headers("Content-Type: application/json")
    Single<JsonObject> getTracingTan(@Body DataTransferRequestData data);

    /*
        Supported Version Numbers
     */

    @GET("versions/apps/android")
    Single<JsonObject> getSupportedVersionNumber();

    /*
        TAN
     */

    @POST("sms/request")
    @Headers("Content-Type: application/json")
    Single<JsonObject> requestPhoneNumberVerificationTan(@Body JsonObject message);

    @POST("sms/verify")
    @Headers("Content-Type: application/json")
    Completable verifyPhoneNumber(@Body JsonObject message);

    @POST("sms/verify/bulk")
    @Headers("Content-Type: application/json")
    Completable verifyPhoneNumberBulk(@Body JsonObject message);

    /*
        Documents
     */

    @POST("tests/redeem")
    Completable redeemDocument(@Body JsonObject message);

    @HTTP(method = "DELETE", path = "tests/redeem", hasBody = true)
    Completable unredeemDocument(@Body JsonObject message);

    @GET("testProviders")
    Single<DocumentProviderDataList> getDocumentProviders();

    /*
        Time
     */

    @GET("timesync")
    Single<JsonObject> getServerTime();

}
