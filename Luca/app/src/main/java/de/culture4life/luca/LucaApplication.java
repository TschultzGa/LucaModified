package de.culture4life.luca;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.provider.Settings;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ShareCompat;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;

import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.dataaccess.DataAccessManager;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.endpoints.LucaEndpointsV3;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.service.LucaService;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import hu.akarnokd.rxjava3.debug.RxJavaAssemblyTracking;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import rxdogtag2.RxDogTag;
import timber.log.Timber;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class LucaApplication extends MultiDexApplication {

    public static final boolean IS_USING_STAGING_ENVIRONMENT = !BuildConfig.BUILD_TYPE.equals("production");
    private static final long MAXIMUM_TIMESTAMP_OFFSET = TimeUnit.MINUTES.toMillis(1);
    private static final String INTENT_TYPE_MAIL = "message/rfc822";

    private final PreferencesManager preferencesManager;
    private final CryptoManager cryptoManager;
    private final GenuinityManager genuinityManager;
    private final NetworkManager networkManager;
    private final LucaNotificationManager notificationManager;
    private final LocationManager locationManager;
    private final RegistrationManager registrationManager;
    private final ChildrenManager childrenManager;
    private final CheckInManager checkInManager;
    private final MeetingManager meetingManager;
    private final HistoryManager historyManager;
    private final DataAccessManager dataAccessManager;
    private final DocumentManager documentManager;
    private final GeofenceManager geofenceManager;

    private final CompositeDisposable applicationDisposable;

    private final Set<Activity> startedActivities;

    @Nullable
    private String deepLink;

    public LucaApplication() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            RxJavaAssemblyTracking.enable();

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }

        preferencesManager = new PreferencesManager();
        notificationManager = new LucaNotificationManager();
        locationManager = new LocationManager();
        networkManager = new NetworkManager();
        geofenceManager = new GeofenceManager();
        cryptoManager = new CryptoManager(preferencesManager, networkManager);
        genuinityManager = new GenuinityManager(preferencesManager, networkManager);
        registrationManager = new RegistrationManager(preferencesManager, networkManager, cryptoManager);
        childrenManager = new ChildrenManager(preferencesManager, registrationManager);
        historyManager = new HistoryManager(preferencesManager, childrenManager);
        meetingManager = new MeetingManager(preferencesManager, networkManager, locationManager, historyManager, cryptoManager);
        checkInManager = new CheckInManager(preferencesManager, networkManager, geofenceManager, locationManager, historyManager, cryptoManager, notificationManager);
        dataAccessManager = new DataAccessManager(preferencesManager, networkManager, notificationManager, checkInManager, historyManager, cryptoManager);
        documentManager = new DocumentManager(preferencesManager, networkManager, historyManager, cryptoManager, registrationManager, childrenManager);

        applicationDisposable = new CompositeDisposable();

        startedActivities = new HashSet<>();

        setupErrorHandler();
    }

    private void setupErrorHandler() {
        RxDogTag.install();
        RxJavaPlugins.setErrorHandler(throwable -> {
            if (throwable instanceof UndeliverableException) {
                // This may happen when race conditions cause multiple errors to be emitted.
                // As only one error can be handled by the stream, subsequent errors are undeliverable.
                // See https://github.com/ReactiveX/RxJava/issues/7008
                Timber.w(throwable.getCause(), "Undeliverable exception");
            } else {
                Timber.e(throwable, "Unhandled error");
                // forward the error, most likely crashing the app
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("Creating application");
        if (!isRunningUnitTests()) {
            long initializationStartTimestamp = System.currentTimeMillis();
            initializeBlocking()
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(() -> Timber.d("Blocking initialization completed after %d ms", (System.currentTimeMillis() - initializationStartTimestamp)))
                    .blockingAwait(10, TimeUnit.SECONDS);

            initializeAsync()
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(() -> Timber.d("Async initialization completed after %d ms", (System.currentTimeMillis() - initializationStartTimestamp)))
                    .subscribe();

            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        Timber.d("Application created");
    }

    /**
     * Initializes everything that is required during application creation.
     */
    @CallSuper
    private Completable initializeBlocking() {
        return CryptoManager.setupSecurityProviders()
                .andThen(preferencesManager.initialize(this));
    }

    /**
     * Initializes everything that is not required instantly after application creation.
     */
    @CallSuper
    private Completable initializeAsync() {
        return Completable.mergeArray(
                notificationManager.initialize(this).subscribeOn(Schedulers.io()),
                networkManager.initialize(this).subscribeOn(Schedulers.io()),
                cryptoManager.initialize(this).subscribeOn(Schedulers.io()),
                genuinityManager.initialize(this).subscribeOn(Schedulers.io()),
                locationManager.initialize(this).subscribeOn(Schedulers.io()),
                registrationManager.initialize(this).subscribeOn(Schedulers.io()),
                childrenManager.initialize(this).subscribeOn(Schedulers.io()),
                checkInManager.initialize(this).subscribeOn(Schedulers.io()),
                historyManager.initialize(this).subscribeOn(Schedulers.io()),
                dataAccessManager.initialize(this).subscribeOn(Schedulers.io()),
                documentManager.initialize(this).subscribeOn(Schedulers.io()),
                geofenceManager.initialize(this).subscribeOn(Schedulers.io())
        ).andThen(Completable.mergeArray(
                invokeRotatingBackendPublicKeyUpdate(),
                invokeAccessedDataUpdate(),
                startKeepingDataUpdated()
        ));
    }

    private Completable invokeRotatingBackendPublicKeyUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(cryptoManager.updateDailyKeyPairPublicKey()
                .doOnError(throwable -> {
                    if (throwable instanceof SSLPeerUnverifiedException) {
                        showErrorAsDialog(new ViewError.Builder(this)
                                .withTitle(R.string.error_certificate_pinning_title)
                                .withDescription(R.string.error_certificate_pinning_description)
                                .withCause(throwable)
                                .removeWhenShown()
                                .build());
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.d("Updated rotating backend public key"),
                        throwable -> Timber.w("Unable to update rotating backend public key: %s", throwable.toString())
                )));
    }

    private Completable invokeAccessedDataUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(dataAccessManager.updateIfNecessary()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.d("Updated accessed data"),
                        throwable -> Timber.w("Unable to update accessed data: %s", throwable.toString())
                )));
    }

    private Completable startKeepingDataUpdated() {
        return Completable.fromAction(() -> applicationDisposable.add(keepDataUpdated()
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> Timber.w("Unable to keep data updated: %s", throwable.toString()))
                .retryWhen(error -> error.delay(3, TimeUnit.SECONDS))
                .subscribe()));
    }

    private Completable keepDataUpdated() {
        return Completable.mergeArray(
                monitorCheckedInState(),
                monitorMeetingHostState(),
                checkInManager.monitorCheckOutAtBackend()
        );
    }

    private Completable monitorCheckedInState() {
        return checkInManager.getCheckedInStateChanges()
                .flatMapCompletable(isCheckedIn -> startOrStopServiceIfRequired());
    }

    private Completable monitorMeetingHostState() {
        return meetingManager.getMeetingHostStateChanges()
                .flatMapCompletable(isHostingMeeting -> startOrStopServiceIfRequired());
    }

    private Completable startOrStopServiceIfRequired() {
        return Single.zip(checkInManager.isCheckedIn(), meetingManager.isCurrentlyHostingMeeting(),
                (isCheckedIn, isHostingMeeting) -> isCheckedIn || isHostingMeeting)
                .flatMapCompletable(shouldStartService -> Completable.fromAction(() -> {
                    if (shouldStartService) {
                        startService();
                    } else {
                        stopService();
                    }
                }));
    }

    public void startService() {
        Intent intent = new Intent(this, LucaService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public void stopService() {
        Intent intent = new Intent(this, LucaService.class);
        stopService(intent);
    }

    public void stopIfNotCurrentlyActive() {
        if (!isUiCurrentlyVisible()) {
            stop();
        }
    }

    /**
     * Will exit the current process, effectively stopping the service and the UI.
     */
    @CallSuper
    public void stop() {
        applicationDisposable.dispose();
        dataAccessManager.dispose();
        checkInManager.dispose();
        registrationManager.dispose();
        cryptoManager.dispose();
        historyManager.dispose();
        networkManager.dispose();
        locationManager.dispose();
        notificationManager.dispose();
        preferencesManager.dispose();
        geofenceManager.dispose();
        dataAccessManager.dispose();
        documentManager.dispose();
        stopService();
        Timber.i("Stopping application");
        System.exit(0);
    }

    public void restart() {
        PackageManager packageManager = getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        startActivity(mainIntent);
        stop();
    }

    public void openUrl(@NonNull String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        Context context = getActivityContext();
        if (context == null) {
            context = this;
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public void openAppSettings() {
        Intent intent = new Intent();
        // https://stackoverflow.com/questions/31127116/open-app-permission-settings:
        // This does not work for third party app permission solutions, such as used in the
        // oneplus 2 oxygen os. Settings are managed from a custom settings view in oxygen os
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void openSupportMailIntent() throws ActivityNotFoundException {
        String subject = getString(R.string.app_name) + " " + getString(R.string.menu_support_subject);

        String appVersionName = BuildConfig.VERSION_NAME;
        int appVersionCode = BuildConfig.VERSION_CODE;
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        String androidVersionName = Build.VERSION.RELEASE;
        String deviceInfo = getString(R.string.menu_support_device_info, deviceName, androidVersionName, appVersionName, appVersionCode);
        String supportText = getString(R.string.menu_support_body, deviceInfo);

        ShareCompat.IntentBuilder.from(getActivityContext())
                .setType(INTENT_TYPE_MAIL)
                .addEmailTo(getString(R.string.mail_support))
                .setSubject(subject)
                .setText(supportText)
                .startChooser();
    }

    /**
     * Delete the account data on backend and clear data locally.
     */
    public Completable deleteAccount() {
        return documentManager.unredeemAndDeleteAllDocuments()
                .andThen(registrationManager.deleteRegistrationOnBackend())
                .andThen(preferencesManager.deleteAll())
                .andThen(cryptoManager.deleteAllKeyStoreEntries());
    }

    public Completable handleDeepLink(Uri uri) {
        return Completable.fromAction(() -> deepLink = uri.toString());
    }

    public boolean isInDarkMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public boolean isUiCurrentlyVisible() {
        return !startedActivities.isEmpty();
    }

    public Single<Boolean> isUpdateRequired() {
        return networkManager.getLucaEndpointsV3()
                .flatMap(LucaEndpointsV3::getSupportedVersionNumber)
                .map(jsonObject -> jsonObject.get("minimumVersion").getAsInt())
                .doOnSuccess(versionNumber -> Timber.d("Minimum supported app version number: %d", versionNumber))
                .map(minimumVersionNumber -> BuildConfig.VERSION_CODE < minimumVersionNumber);
    }

    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities.add(activity);
    }

    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities.remove(activity);
    }

    protected void showErrorAsDialog(@NonNull ViewError error) {
        Activity activity = getActivityContext();
        if (activity == null) {
            Timber.w("Unable to show error, no started activity available: %s", error);
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(error.getTitle())
                .setMessage(error.getDescription());

        if (error.isResolvable()) {
            builder.setPositiveButton(error.getResolveLabel(), (dialog, which) -> applicationDisposable.add(error.getResolveAction()
                    .subscribe(
                            () -> Timber.d("Error resolved"),
                            throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                    )));
        } else {
            builder.setPositiveButton(R.string.action_ok, (dialog, which) -> {
                // do nothing
            });
        }

        new BaseDialogFragment(builder).show();
    }

    @Nullable
    private Activity getActivityContext() {
        if (startedActivities.isEmpty()) {
            return null;
        }
        return new ArrayList<>(startedActivities).get(0);
    }

    public static boolean isGooglePlayServicesAvailable(@NonNull Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public static boolean isRunningUnitTests() {
        try {
            Class.forName("de.culture4life.luca.LucaUnitTest");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public GenuinityManager getGenuinityManager() {
        return genuinityManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public LucaNotificationManager getNotificationManager() {
        return notificationManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    public ChildrenManager getChildrenManager() {
        return childrenManager;
    }

    public CheckInManager getCheckInManager() {
        return checkInManager;
    }

    public MeetingManager getMeetingManager() {
        return meetingManager;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public DataAccessManager getDataAccessManager() {
        return dataAccessManager;
    }

    public DocumentManager getDocumentManager() {
        return documentManager;
    }

    public GeofenceManager getGeofenceManager() {
        return geofenceManager;
    }

    public Maybe<String> getDeepLink() {
        return Maybe.fromCallable(() -> deepLink);
    }

    public void onDeepLinkHandled(@NonNull String url) {
        deepLink = null;
    }

}
