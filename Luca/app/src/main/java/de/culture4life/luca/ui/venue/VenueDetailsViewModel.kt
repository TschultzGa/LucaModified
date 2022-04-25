package de.culture4life.luca.ui.venue

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.URLUtil
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.tbruyelle.rxpermissions3.Permission
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInData
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.checkin.CheckOutException
import de.culture4life.luca.children.Child
import de.culture4life.luca.children.ChildrenManager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.location.GeofenceManager
import de.culture4life.luca.location.LocationManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.ui.BaseViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.children.ChildrenFragment
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.TimeUtil.getReadableTime
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class VenueDetailsViewModel(application: Application) : BaseViewModel(application) {

    enum class UrlType {
        MENU, PROGRAM, MAP, WEBSITE, GENERAL
    }

    private val preferenceManager: PreferencesManager = this.application.preferencesManager
    private val childrenManager: ChildrenManager = this.application.childrenManager
    private val checkInManager: CheckInManager = this.application.checkInManager
    private val geofenceManager: GeofenceManager = this.application.geofenceManager
    private val locationManager: LocationManager = this.application.locationManager
    private val consentManager: ConsentManager = this.application.consentManager

    val id = MutableLiveData<UUID>()
    val title = MutableLiveData<String>()
    val subtitle = MutableLiveData<String>()
    val additionalDataTitle = MutableLiveData<String>()
    val additionalDataValue = MutableLiveData<String>()
    val showAdditionalData = MutableLiveData(false)
    val checkInTime = MutableLiveData<String>()
    val checkInDuration = MutableLiveData<String>()
    val isCheckedIn = MutableLiveData(false)
    val hasLocationRestriction = MutableLiveData(false)
    val isGeofencingSupported = MutableLiveData(true)
    val shouldEnableAutomaticCheckOut = MutableLiveData<Boolean>()
    val shouldEnableLocationServices = MutableLiveData<Boolean>()
    val childCounter = MutableLiveData<Int>()
    val bundle = MutableLiveData<Bundle?>()
    val providedUrls: MutableLiveData<List<Pair<UrlType, String>>> = MutableLiveData(ArrayList())
    val checkInData: MutableLiveData<CheckInData> = MutableLiveData()
    val askUrlConsent = MutableLiveData<UrlType>()

    private var isLocationPermissionGranted = false
    private var isBackgroundLocationPermissionGranted = false
    private var askedForFineLocationPermissionAlready = false
    private var updatedProvidedUrlsError: ViewError? = null
    private var checkOutError: ViewError? = null

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(
                Completable.mergeArray(
                    preferenceManager.initialize(application),
                    checkInManager.initialize(application),
                    geofenceManager.initialize(application),
                    locationManager.initialize(application),
                    consentManager.initialize(application)
                )
            )
            .andThen(
                geofenceManager.isGeofencingSupported
                    .flatMapCompletable { update(isGeofencingSupported, it) }
            )
            .andThen(
                checkInManager.isCheckedIn
                    .flatMapCompletable { update(isCheckedIn, it) }
            )
            .andThen(
                checkInManager.checkInDataIfAvailable
                    .doOnSuccess { checkInData ->
                        Timber.d("Check-in data: %s", checkInData)
                        updateAsSideEffect(this.checkInData, checkInData)
                        updateAsSideEffect(id, checkInData.locationId)
                        updateAsSideEffect(hasLocationRestriction, checkInData.hasLocationRestriction())
                        if (checkInData.locationAreaName != null) {
                            updateAsSideEffect(subtitle, checkInData.locationGroupName)
                            updateAsSideEffect(title, checkInData.locationAreaName)
                        } else {
                            updateAsSideEffect(subtitle, null)
                            updateAsSideEffect(title, checkInData.locationGroupName)
                        }
                    }
                    .ignoreElement()
            )
            .andThen(initializeAutomaticCheckout())
            .andThen(invokeProvidedUrlsUpdate())
            .andThen(updateChildCounter())
    }

    private fun initializeAutomaticCheckout(): Completable {
        return Single.zip(
            checkInManager.checkInDataIfAvailable
                .map(CheckInData::hasLocationRestriction)
                .defaultIfEmpty(false),
            checkInManager.isAutomaticCheckoutEnabled,
            preferenceManager.restoreOrDefault(KEY_AUTOMATIC_CHECKOUT_ENABLED, false)
        ) { hasLocationRestriction, isGeofenceActive, automaticCheckoutEnabled ->
            Completable.defer {
                if (isGeofenceActive) {
                    startObservingAutomaticCheckOutErrors()
                } else {
                    Completable.complete()
                }
            }.andThen(
                update(
                    shouldEnableAutomaticCheckOut, isGeofenceActive || (hasLocationRestriction && automaticCheckoutEnabled)
                )
            )
        }
            .flatMapCompletable { it }
    }

    fun updateLocationServicesStatus(): Completable {
        return Single.fromCallable { isLocationServiceEnabled }
            .flatMapCompletable { enabled ->
                if (!enabled) {
                    disableAutomaticCheckout()
                }
                updateIfRequired(shouldEnableLocationServices, !enabled)
            }
    }

    private fun updateChildCounter(): Completable {
        return childrenManager.getCheckedInChildren()
            .map(List<Child>::size)
            .flatMapCompletable { update(childCounter, it) }
    }

    private fun startObservingAutomaticCheckOutErrors(): Completable {
        return Completable.fromAction {
            modelDisposable.add(
                checkInManager.autoCheckoutGeofenceRequest
                    .flatMapObservable { Observable.fromIterable(it.geofences) }
                    .flatMap(geofenceManager::getGeofenceEvents)
                    .ignoreElements()
                    .onErrorResumeNext(::handleAutomaticCheckOutError)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            )
        }
    }

    override fun keepDataUpdated(): Completable {
        return Completable.mergeArray(
            super.keepDataUpdated(),
            keepCheckedInStateUpdated(),
            keepCheckedInTimerUpdated(),
            keepAdditionalDataUpdated()
        )
    }

    private fun keepCheckedInStateUpdated(): Completable {
        return checkInManager.checkedInStateChanges
            .flatMapCompletable { checkedInState ->
                Completable.fromAction {
                    if (!checkedInState) {
                        updateAsSideEffect(shouldEnableAutomaticCheckOut, false)
                    }
                }.andThen(update(isCheckedIn, checkedInState))
            }
    }

    private fun keepCheckedInTimerUpdated(): Completable {
        return checkInManager.checkInDataAndChanges
            .map(CheckInData::timestamp)
            .switchMapCompletable {
                Completable.mergeArray(
                    updateReadableCheckInTime(it),
                    updateReadableCheckInDuration(it)
                )
            }
    }

    private fun keepAdditionalDataUpdated(): Completable {
        return checkInManager.isCheckedInToPrivateMeeting
            .flatMapCompletable { inPrivateMeeting ->
                if (inPrivateMeeting) {
                    // don't show additional data in private meetings
                    return@flatMapCompletable Completable.complete()
                } else {
                    return@flatMapCompletable checkInManager.additionalCheckInPropertiesAndChanges
                        .flatMapCompletable { properties ->
                            Completable.fromAction {
                                Timber.d("Additional check-in properties: %s", properties)
                                updateAsSideEffect(showAdditionalData, properties.keySet().isNotEmpty())
                                for (key in properties.keySet()) {
                                    updateAsSideEffect(additionalDataTitle, getAdditionalDataTitle(key))
                                    updateAsSideEffect(additionalDataValue, getAdditionalDataValue(properties[key]))
                                }
                            }
                        }
                }
            }
    }

    private fun updateReadableCheckInTime(timestamp: Long): Completable {
        return Single.fromCallable { getReadableTime(application, timestamp) }
            .flatMapCompletable { update(checkInTime, it) }
    }

    private fun updateReadableCheckInDuration(timestamp: Long): Completable {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
            .map { TimeUtil.getCurrentMillis() - timestamp }
            .map(TimeUtil::getReadableTimeDuration)
            .flatMapCompletable { update(checkInDuration, it) }
    }

    fun onSlideCompleted() {
        if (isCheckedIn.value!!) {
            onCheckOutRequested()
        } else {
            onCheckInRequested()
        }
    }

    fun openChildrenView() {
        if (isCurrentDestinationId(R.id.venueDetailFragment)) {
            val extras = Bundle()
            extras.putBoolean(ChildrenFragment.CHECK_IN, true)
            navigationController!!.navigate(R.id.action_venueDetailsFragment_to_childrenFragment, extras)
        }
    }

    /**
     * Manual check in request by the user. As he should already be checked in, this is not
     * implemented.
     */
    fun onCheckInRequested() {
        Completable.error(RuntimeException("Not implemented"))
            .doOnSubscribe { updateAsSideEffect(isLoading, true) }
            .doOnComplete { updateAsSideEffect(isCheckedIn, true) }
            .doFinally { updateAsSideEffect(isLoading, false) }
            .subscribe(
                { Timber.i("Checked in") },
                { Timber.w("Unable to check in: %s", it.toString()) }
            )
            .addTo(modelDisposable)
    }

    /**
     * Manual check out by the user.
     */
    fun onCheckOutRequested() {
        checkInManager.checkOut()
            .doOnSubscribe {
                updateAsSideEffect(isLoading, true)
                removeError(checkOutError)
            }
            .doOnComplete { updateAsSideEffect(isCheckedIn, false) }
            .doFinally { updateAsSideEffect(isLoading, false) }
            .doOnError {
                if (it is CheckOutException) {
                    val error = it.errorCode
                    if (error == CheckOutException.LOCATION_UNAVAILABLE_ERROR ||
                        error == CheckOutException.MISSING_PERMISSION_ERROR
                    ) {
                        checkInManager.setSkipMinimumDistanceAssertion(true)
                    }
                }
                checkOutError = createCheckOutViewError(it).build()
                addError(checkOutError)
            }
            .subscribe(
                { Timber.i("Checked out") },
                { Timber.w("Unable to check out: %s", it.toString()) }
            )
            .addTo(modelDisposable)
    }

    /**
     * Check if all conditions for activating automatic check-out are met, if not try to enable them
     * one by one.
     *
     * @return `true` if permissions are granted and location is active, `false`
     * otherwise.
     */
    private fun enableAutomaticCheckoutActivation(): Boolean {
        // Coarse location access is not enough for geofencing
        if (ActivityCompat.checkSelfPermission(application, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ActivityCompat.checkSelfPermission(application, ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBackgroundLocationPermission()
            return false
        }
        if (!isLocationServiceEnabled) {
            updateAsSideEffect(shouldEnableLocationServices, true)
            return false
        }
        return true
    }

    fun isLocationConsentGiven(): Single<Boolean> {
        return preferenceManager.restoreOrDefault(KEY_LOCATION_CONSENT_GIVEN, false)
    }

    fun setLocationConsentGiven() {
        preferenceManager.persist(KEY_LOCATION_CONSENT_GIVEN, true)
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Enable automatic check out if all conditions are met.
     */
    @SuppressLint("MissingPermission")
    fun enableAutomaticCheckout() {
        if (checkInManager.isAutomaticCheckoutEnabled.blockingGet() || !enableAutomaticCheckoutActivation()) {
            return
        }
        checkInManager.enableAutomaticCheckOut()
            .andThen(update(shouldEnableAutomaticCheckOut, true))
            .andThen(startObservingAutomaticCheckOutErrors())
            .doOnError {
                onEnablingAutomaticCheckOutFailed()
                handleAutomaticCheckOutError(it)
                    .onErrorComplete()
                    .subscribe()
                    .addTo(modelDisposable)
            }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.v("Automatic check-out was enabled") },
                { Timber.w(it, "Unable to enable automatic check-out") }
            )
            .addTo(modelDisposable)
    }

    fun disableAutomaticCheckout() {
        checkInManager.disableAutomaticCheckOut()
            .andThen(update(shouldEnableAutomaticCheckOut, false))
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.v("Automatic check-out was disabled") },
                { Timber.w(it, "Unable to disable automatic check-out") }
            )
            .addTo(modelDisposable)
    }

    private fun handleAutomaticCheckOutError(throwable: Throwable): Completable {
        return Completable.fromAction {
            val errorBuilder = ViewError.Builder(application)
                .withTitle(R.string.auto_checkout_generic_error_title)
                .withCause(throwable)
                .removeWhenShown()
                .canBeShownAsNotification()
            if (!locationManager.isLocationServiceEnabled) {
                errorBuilder.isExpected()
                    .withTitle(R.string.auto_checkout_location_disabled_title)
                    .withDescription(R.string.auto_checkout_location_disabled_description)
            }
            addError(errorBuilder.build())
            disableAutomaticCheckout()
        }
    }

    fun changeLocation() {
        onCheckOutRequested()
    }

    /*
        Permission handling
     */
    override fun onPermissionResult(permission: Permission) {
        super.onPermissionResult(permission)
        if (permission.granted) {
            onPermissionGranted(permission)
        } else {
            onPermissionDenied(permission)
        }
    }

    @SuppressLint("InlinedApi")
    private fun onPermissionGranted(permission: Permission) {
        when (permission.name) {
            ACCESS_FINE_LOCATION -> {
                if (!isLocationPermissionGranted) {
                    onLocationPermissionGranted()
                    isLocationPermissionGranted = true
                }
            }
            ACCESS_BACKGROUND_LOCATION -> {
                if (!isBackgroundLocationPermissionGranted) {
                    onBackgroundLocationPermissionGranted()
                    isBackgroundLocationPermissionGranted = true
                }
            }
        }
    }

    /**
     * Handles granting of the Manifest.permission.ACCESS_FINE_LOCATION permission.
     */
    private fun onLocationPermissionGranted() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        } else {
            enableAutomaticCheckout()
        }
    }

    private fun onBackgroundLocationPermissionGranted() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, true)
        enableAutomaticCheckout()
    }

    @JvmOverloads
    fun onPermissionDenied(permission: Permission, shouldShowRationale: Boolean = permission.shouldShowRequestPermissionRationale) {
        when (permission.name) {
            ACCESS_FINE_LOCATION -> {
                // If we only have coarse location access we need to ask again to gain fine access to make geofencing work
                // But we should only do this once, otherwise we can end up in an infinite loop
                if (askedForFineLocationPermissionAlready) {
                    onLocationPermissionDenied(shouldShowRationale)
                } else {
                    askedForFineLocationPermissionAlready = true
                    requestLocationPermission()
                }
            }
            ACCESS_BACKGROUND_LOCATION -> {
                onBackgroundLocationPermissionDenied(shouldShowRationale)
            }
        }
    }

    private fun onLocationPermissionDenied(shouldShowRationale: Boolean) {
        onEnablingAutomaticCheckOutFailed()
    }

    private fun onBackgroundLocationPermissionDenied(shouldShowRationale: Boolean) {
        onLocationPermissionDenied(shouldShowRationale)
    }

    fun onEnablingAutomaticCheckOutFailed() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, false)
    }

    private fun createCheckOutViewError(throwable: Throwable): ViewError.Builder {
        val builder = createErrorBuilder(throwable)
            .withTitle(R.string.venue_check_out_error)
        if (throwable is CheckOutException) {
            when (throwable.errorCode) {
                CheckOutException.MISSING_PERMISSION_ERROR -> {
                    builder.withDescription(R.string.venue_check_out_error_permission)
                        .withResolveAction(Completable.fromAction { requestLocationPermission() })
                        .withResolveLabel(R.string.action_grant)
                }
                CheckOutException.MINIMUM_DURATION_ERROR -> {
                    builder.withDescription(R.string.venue_check_out_error_duration)
                }
                CheckOutException.MINIMUM_DISTANCE_ERROR -> {
                    builder.withDescription(R.string.venue_check_out_error_in_range)
                }
                CheckOutException.LOCATION_UNAVAILABLE_ERROR -> {
                    builder.withDescription(R.string.venue_check_out_error_location_unavailable)
                }
                else -> {
                    builder.withDescription(R.string.error_generic_title)
                }
            }
        }
        return builder
    }

    fun requestLocationPermissionForAutomaticCheckOut() {
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        // Since Android 12 we need to ask for both fine AND coarse at the same time
        addPermissionToRequiredPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun requestBackgroundLocationPermissionForAutomaticCheckOut() {
        requestBackgroundLocationPermission()
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun requestBackgroundLocationPermission() {
        addPermissionToRequiredPermissions(ACCESS_BACKGROUND_LOCATION)
    }

    private fun getAdditionalDataTitle(property: String): String {
        val readableProperty: String = when (property) {
            "table" -> {
                application.getString(R.string.venue_property_table)
            }
            "patient" -> {
                application.getString(R.string.venue_property_patient_name)
            }
            "ln" -> {
                application.getString(R.string.venue_property_meeting_name)
            }
            else -> {
                property.substring(0, 1).uppercase(Locale.getDefault()) + property.substring(1)
            }
        }
        return "$readableProperty:"
    }

    private fun getAdditionalDataValue(jsonElement: JsonElement?): String {
        if (jsonElement == null || jsonElement is JsonNull) {
            return application.getString(R.string.unknown)
        } else if (jsonElement.isJsonPrimitive) {
            val jsonPrimitive = jsonElement.asJsonPrimitive
            if (jsonPrimitive.isString) {
                return jsonPrimitive.asString
            }
        }
        return jsonElement.toString()
    }

    /*
        URLs
     */

    private fun invokeProvidedUrlsUpdate(): Completable {
        return Completable.fromAction {
            updateProvidedUrls()
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(modelDisposable)
        }
    }

    private fun updateProvidedUrls(): Completable {
        return checkInManager.checkInDataIfAvailable
            .map { it.locationId.toString() }
            .flatMapObservable(this::fetchProvidedUrls)
            .toList()
            .flatMapCompletable { updateIfRequired(providedUrls, it) }
            .doOnSubscribe {
                removeError(updatedProvidedUrlsError)
                updateAsSideEffectIfRequired(isLoading, true)
            }
            .doOnError {
                updatedProvidedUrlsError = ViewError.Builder(application)
                    .withCause(it)
                    .withTitle(application.getString(R.string.error_request_failed_title))
                    .withResolveLabel(application.getString(R.string.action_retry))
                    .withResolveAction(invokeProvidedUrlsUpdate())
                    .build()
                addError(updatedProvidedUrlsError)
            }
            .doFinally { updateAsSideEffectIfRequired(isLoading, false) }
    }

    private fun fetchProvidedUrls(locationId: String): Observable<Pair<UrlType, String>> {
        return application.networkManager.getLucaEndpointsV3()
            .flatMap { it.getLocationUrls(locationId) }
            .map { urlsResponseData ->
                val urls = ArrayList<Pair<UrlType, String>>()
                with(urlsResponseData) {
                    menu?.also { urls.add(Pair(UrlType.MENU, it)) }
                    program?.also { urls.add(Pair(UrlType.PROGRAM, it)) }
                    map?.also { urls.add(Pair(UrlType.MAP, it)) }
                    website?.also { urls.add(Pair(UrlType.WEBSITE, it)) }
                    general?.also { urls.add(Pair(UrlType.GENERAL, it)) }
                }
                return@map urls
            }
            .flatMapObservable { Observable.fromIterable(it) }
            .filter {
                val url = it.second
                URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)
            }
            .doOnNext { (type, url) -> Timber.v("%s: %s", type, url) }
            .doOnSubscribe { Timber.d("Fetching provided URLs") }
    }

    fun getProvidedUrl(urlType: UrlType): String? {
        return providedUrls.value?.firstOrNull { (type, _) -> type == urlType }?.second
    }

    fun openProvidedUrlOrAskConsent(urlType: UrlType) {
        consentManager.getConsent(ConsentManager.ID_OPEN_VENUE_URL)
            .doOnSuccess { consent ->
                if (consent.approved) {
                    getProvidedUrl(urlType)?.also { application.openUrl(it) }
                } else {
                    updateAsSideEffect(askUrlConsent, urlType)
                }
            }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
            .addTo(modelDisposable)
    }

    fun reportAbuse(activity: Activity) {
        val locationName = checkInData.value?.locationDisplayName ?: application.getString(R.string.unknown)
        val locationId = checkInData.value?.locationId ?: application.getString(R.string.unknown)
        val locationUrls = providedUrls.value?.joinToString("\n") { (_, url) -> "- $url" }
            ?: application.getString(R.string.unknown)

        val text = application.getString(R.string.venue_url_report_description, locationName, locationId, locationUrls)

        ShareCompat.IntentBuilder(activity)
            .setType(LucaApplication.INTENT_TYPE_MAIL)
            .addEmailTo(application.getString(R.string.mail_abuse))
            .setSubject(application.getString(R.string.venue_url_report_title))
            .setText(text)
            .startChooser()
    }

    /*
        Getter & Setter
    */

    val isLocationServiceEnabled: Boolean
        get() = locationManager.isLocationServiceEnabled

    fun setBundle(bundle: Bundle?) {
        this.bundle.value = bundle
    }

    fun setAutomaticCheckoutActiveAsDefault(shouldBeActive: Boolean) {
        preferenceManager.persist(KEY_AUTOMATIC_CHECKOUT_ENABLED, shouldBeActive)
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    companion object {
        const val KEY_LOCATION_CONSENT_GIVEN = "location_consent_given"
        const val KEY_AUTOMATIC_CHECKOUT_ENABLED = "automatic_checkout_enabled"
    }
}
