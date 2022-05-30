package de.culture4life.luca.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.GsonBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.network.endpoints.AttestationEndpoints
import de.culture4life.luca.network.endpoints.LucaEndpointsV3
import de.culture4life.luca.network.endpoints.LucaEndpointsV4
import de.culture4life.luca.network.endpoints.LucaIdEndpoints
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import javax.net.ssl.TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;


class NetworkManager : Manager() {
    private val rxAdapter: RxJava3CallAdapterFactory by lazy { RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io()) }
    private var lucaEndpointsV3: LucaEndpointsV3? = null
    private var lucaEndpointsV4: LucaEndpointsV4? = null
    private var attestationEndpoints: AttestationEndpoints? = null
    private var lucaIdEndpoints: LucaIdEndpoints? = null
    private lateinit var connectivityManager: ConnectivityManager
    private val connectivityStateSubject = BehaviorSubject.create<Boolean>()
    private var serverAddress = BuildConfig.API_BASE_URL
    private var connectivityReceiver: BroadcastReceiver? = null

    private val gsonFactory: GsonConverterFactory by lazy {
        val gson = GsonBuilder()
            .setLenient()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            .create()
        GsonConverterFactory.create(gson)
    }

    private val okHttpClient: OkHttpClient by lazy { createOkHttpClient() }

    override fun doInitialize(context: Context): Completable {
        return initializeConnectivityReceiver()
    }

    override fun dispose() {
        super.dispose()
        unregisterConnectivityReceiver()
    }

    private fun createRetrofit(baseUrlSuffix: String): Retrofit {
        return createRetrofit(serverAddress, baseUrlSuffix)
    }

    private fun createRetrofit(baseUrl: String, baseUrlSuffix: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl + baseUrlSuffix)
            .addConverterFactory(gsonFactory)
            .addCallAdapterFactory(rxAdapter)
            .client(okHttpClient)
            .build()
    }

    private fun createOkHttpClient(): OkHttpClient {
        val userAgentInterceptor = Interceptor { chain: Interceptor.Chain ->
            chain.proceed(
                chain.request()
                    .newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
        }
        val builder: OkHttpClient.Builder = getUnsafeOkHttpClient()
            .addInterceptor(userAgentInterceptor)

        if (BuildConfig.DEBUG) {
            // Interceptor that shows all network requests as a notification in debug builds
            val chuckerInterceptor: ChuckerInterceptor = ChuckerInterceptor.Builder(context).build()
            builder.addNetworkInterceptor(chuckerInterceptor)
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            builder.addInterceptor(loggingInterceptor)
        }
        if (LucaApplication.IS_USING_STAGING_ENVIRONMENT) {
            builder.authenticator { _, response: Response ->
                val credential = basic(BuildConfig.STAGING_API_USERNAME, BuildConfig.STAGING_API_PASSWORD)
                response.request.newBuilder()
                    .header("Authorization", credential)
                    .build()
            }
            val rateLimitInterceptor = Interceptor { chain: Interceptor.Chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("X-Rate-Limit-Bypass", "1")
                        .build()
                )
            }
            builder.addInterceptor(rateLimitInterceptor)
        }
        return builder.build()
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient.Builder{
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object:  X509TrustManager {

                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    }


                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory  = sslContext.socketFactory
            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getLucaEndpointsV3(): Single<LucaEndpointsV3> {
        return Single.fromCallable {
            if (lucaEndpointsV3 == null) {
                lucaEndpointsV3 = createRetrofit("/api/v" + 3 + "/").create(LucaEndpointsV3::class.java)
            }
            lucaEndpointsV3
        }
    }

    fun getLucaEndpointsV4(): Single<LucaEndpointsV4> {
        return Single.fromCallable {
            if (lucaEndpointsV4 == null) {
                lucaEndpointsV4 = createRetrofit("/api/v" + 4 + "/").create(LucaEndpointsV4::class.java)
            }
            lucaEndpointsV4
        }
    }

    fun getAttestationEndpoints(): Single<AttestationEndpoints> {
        return Single.fromCallable {
            if (attestationEndpoints == null) {
                attestationEndpoints = createRetrofit("/attestation/api/v1/").create(AttestationEndpoints::class.java)
            }
            attestationEndpoints
        }
    }

    fun getLucaIdEndpoints(): Single<LucaIdEndpoints> {
        return Single.fromCallable {
            if (lucaIdEndpoints == null) {
                lucaIdEndpoints = createRetrofit("/id/api/v1/").create(LucaIdEndpoints::class.java)
            }
            lucaIdEndpoints
        }
    }

    fun assertNetworkConnected(): Completable {
        return isNetworkConnected()
            .flatMapCompletable { isNetworkConnected: Boolean ->
                if (isNetworkConnected) {
                    return@flatMapCompletable Completable.complete()
                } else {
                    return@flatMapCompletable Completable.error(NetworkUnavailableException("Network is not connected"))
                }
            }
    }

    fun isNetworkConnected(): Single<Boolean> {
        return Single.defer { getInitializedField(connectivityManager) }
            .map { manager -> manager.activeNetworkInfo?.isConnectedOrConnecting ?: false }
    }

    fun getConnectivityStateAndChanges(): Observable<Boolean> = connectivityStateSubject

    private fun initializeConnectivityReceiver(): Completable {
        return Completable.fromAction {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    managerDisposable.add(
                        isNetworkConnected()
                            .doOnSuccess { isConnected: Boolean? -> Timber.d("Connectivity state changed: isConnected=%b", isConnected) }
                            .doOnSuccess { t: Boolean -> connectivityStateSubject.onNext(t) }
                            .onErrorComplete()
                            .subscribe()
                    )
                }
            }
            context.registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }
    }



    private fun unregisterConnectivityReceiver() {
        if (connectivityReceiver != null) {
            context.unregisterReceiver(connectivityReceiver)
            connectivityReceiver = null
            connectivityStateSubject.onComplete()
        }
    }

    // TODO Find better way to change server for automated tests.
    fun overrideServerAddress(serverAddress: HttpUrl) {
        // Ensure we are running automated tests to avoid exploits in release variants.
        if (LucaApplication.isRunningUnitTests() || LucaApplication.isRunningInstrumentationTests()) {
            val trailingSlash = "/$".toRegex()
            this.serverAddress = serverAddress.toUrl().toString().replaceFirst(trailingSlash, "")
            if (LucaApplication.isRunningInstrumentationTests()) {
                // Force recreation to ensure new server address is used for each instrumentationTest method execution.
                // Alternative we could keep the mock server running until all methods are executed and only reset the
                // state instead of restarting the server. Or ensure managers do reset between each method execution.
                lucaEndpointsV3 = null
                lucaEndpointsV4 = null
                attestationEndpoints = null
                lucaIdEndpoints = null
            }
        }
    }

    companion object {
        const val HTTP_UPGRADE_REQUIRED = 426
        private const val CACHE_SIZE = 1024 * 1024 * 10
        private val DEFAULT_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(10)
        private val USER_AGENT = createUserAgent()
        private fun createUserAgent(): String {
            val appVersionName = BuildConfig.VERSION_NAME
            return "luca/Android $appVersionName"
        }

        private fun getRequestTimeout(request: Request): Long {
            val timeout: Long
            val path = request.url.encodedPath
            timeout = if (path.contains("/sms/request")) {
                TimeUnit.SECONDS.toMillis(45)
            } else {
                DEFAULT_REQUEST_TIMEOUT
            }
            return timeout
        }

        internal fun useCdn(request: Request): Boolean {
            val path = request.url.encodedPath
            return path.contains("notifications") || path.contains("healthDepartments")
        }

        internal fun replaceHostWithCdn(request: Request): Request {
            val cdnHost = request.url.host.replaceFirst("app".toRegex(), "data")
            val cdnUrl = request.url.newBuilder()
                .host(cdnHost)
                .build()
            return request.newBuilder()
                .url(cdnUrl)
                .build()
        }

        internal fun replaceHostWithCdnIfRequired(request: Request): Request {
            return if (useCdn(request)) {
                replaceHostWithCdn(request)
            } else {
                request
            }
        }

        @JvmStatic
        fun isHttpException(throwable: Throwable, vararg expectedStatusCodes: Int): Boolean {
            if (throwable !is HttpException) {
                return false
            }
            if (expectedStatusCodes.isEmpty()) {
                return true
            }
            val actualStatusCode = throwable.code()
            return expectedStatusCodes.contains(actualStatusCode)
        }
    }
}
