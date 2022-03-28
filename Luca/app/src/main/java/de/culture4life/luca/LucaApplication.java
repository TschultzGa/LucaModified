package de.culture4life.luca;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_EVENT;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
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
import de.culture4life.luca.connect.ConnectManager;
import de.culture4life.luca.consent.ConsentManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.dataaccess.DataAccessManager;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.health.HealthDepartmentManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.endpoints.LucaEndpointsV3;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.pow.PowManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.service.LucaService;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.ui.splash.SplashActivity;
import de.culture4life.luca.util.StrictModeUtil;
import de.culture4life.luca.util.TimeUtil;
import de.culture4life.luca.whatisnew.WhatIsNewManager;
import hu.akarnokd.rxjava3.debug.RxJavaAssemblyTracking;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import rxdogtag2.RxDogTag;
import timber.log.Timber;

public class LucaApplication extends MultiDexApplication {

    public static final boolean IS_USING_STAGING_ENVIRONMENT = !BuildConfig.BUILD_TYPE.equals("production");
    public static final String INTENT_TYPE_MAIL = "message/rfc822";

    private PreferencesManager preferencesManager;
    private LucaNotificationManager notificationManager;
    private LocationManager locationManager;
    private NetworkManager networkManager;
    private GeofenceManager geofenceManager;
    private PowManager powManager;
    private ConsentManager consentManager;
    private WhatIsNewManager whatIsNewManager;
    private GenuinityManager genuinityManager;
    private CryptoManager cryptoManager;
    private RegistrationManager registrationManager;
    private ChildrenManager childrenManager;
    private HistoryManager historyManager;
    private HealthDepartmentManager healthDepartmentManager;
    private MeetingManager meetingManager;
    private CheckInManager checkInManager;
    private DataAccessManager dataAccessManager;
    private DocumentManager documentManager;
    private ConnectManager connectManager;

    private final CompositeDisposable applicationDisposable;

    private final Set<Activity> startedActivities;

    @Nullable
    private String deepLink;

    public LucaApplication() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            if (!(isRunningUnitTests() || isRunningInstrumentationTests())) {
                RxJavaAssemblyTracking.enable();
                StrictModeUtil.INSTANCE.enableStrictMode();
            }
        }

        preferencesManager = new PreferencesManager();
        notificationManager = new LucaNotificationManager();
        locationManager = new LocationManager();
        networkManager = new NetworkManager();
        geofenceManager = new GeofenceManager();
        powManager = new PowManager(networkManager);
        consentManager = new ConsentManager(preferencesManager);
        genuinityManager = new GenuinityManager(preferencesManager, networkManager);
        cryptoManager = new CryptoManager(preferencesManager, networkManager, genuinityManager);
        registrationManager = new RegistrationManager(preferencesManager, networkManager, cryptoManager);
        whatIsNewManager = new WhatIsNewManager(preferencesManager, notificationManager, registrationManager);
        childrenManager = new ChildrenManager(preferencesManager, registrationManager);
        historyManager = new HistoryManager(preferencesManager, childrenManager);
        healthDepartmentManager = new HealthDepartmentManager(preferencesManager, networkManager, consentManager, registrationManager, cryptoManager);
        meetingManager = new MeetingManager(preferencesManager, networkManager, locationManager, historyManager, cryptoManager);
        checkInManager = new CheckInManager(preferencesManager, networkManager, geofenceManager, locationManager, historyManager, cryptoManager, notificationManager, genuinityManager);
        dataAccessManager = new DataAccessManager(preferencesManager, networkManager, notificationManager, checkInManager, historyManager, cryptoManager);
        documentManager = new DocumentManager(preferencesManager, networkManager, historyManager, cryptoManager, registrationManager, childrenManager);
        connectManager = new ConnectManager(preferencesManager, notificationManager, networkManager, powManager, cryptoManager, registrationManager, documentManager, healthDepartmentManager, whatIsNewManager);

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
        if (!(isRunningUnitTests() || isRunningInstrumentationTests())) {
            long initializationStartTimestamp = TimeUtil.getCurrentMillis();
            initializeBlocking()
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(() -> Timber.d("Blocking initialization completed after %d ms", (TimeUtil.getCurrentMillis() - initializationStartTimestamp)))
                    .blockingAwait(10, TimeUnit.SECONDS);

            initializeAsync()
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(() -> Timber.d("Async initialization completed after %d ms", (TimeUtil.getCurrentMillis() - initializationStartTimestamp)))
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
                powManager.initialize(this).subscribeOn(Schedulers.io()),
                consentManager.initialize(this).subscribeOn(Schedulers.io()),
                whatIsNewManager.initialize(this).subscribeOn(Schedulers.io()),
                cryptoManager.initialize(this).subscribeOn(Schedulers.io()),
                genuinityManager.initialize(this).subscribeOn(Schedulers.io()),
                locationManager.initialize(this).subscribeOn(Schedulers.io()),
                registrationManager.initialize(this).subscribeOn(Schedulers.io()),
                childrenManager.initialize(this).subscribeOn(Schedulers.io()),
                checkInManager.initialize(this).subscribeOn(Schedulers.io()),
                historyManager.initialize(this).subscribeOn(Schedulers.io()),
                dataAccessManager.initialize(this).subscribeOn(Schedulers.io()),
                documentManager.initialize(this).subscribeOn(Schedulers.io()),
                geofenceManager.initialize(this).subscribeOn(Schedulers.io()),
                connectManager.initialize(this).subscribeOn(Schedulers.io()),
                healthDepartmentManager.initialize(this).subscribeOn(Schedulers.io())
        ).andThen(Completable.mergeArray(
                invokeRotatingBackendPublicKeyUpdate(),
                invokeAccessedDataUpdate(),
                startKeepingDataUpdated(),
                invokeCheckUpdateRequired()
        ));
    }

    private Completable invokeRotatingBackendPublicKeyUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(cryptoManager.updateDailyPublicKey()
                .doOnError(throwable -> {
                    if (throwable instanceof SSLPeerUnverifiedException) {
                        showErrorAsDialog(new ViewError.Builder(this)
                                .withCause(throwable)
                                .removeWhenShown()
                                .build());
                    }
                })
                .delaySubscription(1, TimeUnit.SECONDS, Schedulers.io())
                .subscribe(
                        () -> Timber.d("Updated rotating backend public key"),
                        throwable -> Timber.w("Unable to update rotating backend public key: %s", throwable.toString())
                )));
    }

    private Completable invokeAccessedDataUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(dataAccessManager.updateIfNecessary()
                .delaySubscription(3, TimeUnit.SECONDS, Schedulers.io())
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
        invalidateAppState();
        Timber.i("Stopping application");
        System.exit(0);
    }

    public void invalidateAppState() {
        preferencesManager.dispose();
        notificationManager.dispose();
        locationManager.dispose();
        networkManager.dispose();
        geofenceManager.dispose();
        powManager.dispose();
        consentManager.dispose();
        whatIsNewManager.dispose();
        genuinityManager.dispose();
        cryptoManager.dispose();
        registrationManager.dispose();
        childrenManager.dispose();
        historyManager.dispose();
        healthDepartmentManager.dispose();
        meetingManager.dispose();
        checkInManager.dispose();
        dataAccessManager.dispose();
        documentManager.dispose();
        connectManager.dispose();

        applicationDisposable.dispose();
        startedActivities.clear();

        stopService();
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
        return connectManager.unEnroll()
                .andThen(documentManager.unredeemAndDeleteAllDocuments())
                .andThen(registrationManager.deleteRegistrationOnBackend())
                .andThen(cryptoManager.deleteAllKeyStoreEntries())
                .andThen(preferencesManager.deleteAll());
    }

    public Completable handleDeepLink(Uri uri) {
        return Completable.fromAction(() -> {
            Timber.i("Setting deeplink: %s", uri);
            deepLink = uri.toString();
        });
    }

    public boolean isInDarkMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public boolean isUiCurrentlyVisible() {
        return !startedActivities.isEmpty();
    }

    private Completable invokeCheckUpdateRequired() {
        return Completable.fromAction(() -> {
            Disposable isUpdateRequiredCheck = checkUpdateRequired()
                    .doOnSubscribe(disposable -> Timber.d("Checking if update is required"))
                    .retryWhen(throwable -> throwable.delay(5, TimeUnit.SECONDS))
                    // Short delay to skip the SplashActivity where it isn't safe to show dialogs.
                    //  Delay should be enough for most devices. On too slow devices we will fall into the retry mechanism.
                    .delaySubscription(500, TimeUnit.MILLISECONDS, Schedulers.io())
                    .subscribe(
                            () -> Timber.d("Update required check done"),
                            throwable -> Timber.w("Unable to check if update is required: %s", throwable.toString())
                    );
            applicationDisposable.add(isUpdateRequiredCheck);
        });
    }

    public Completable checkUpdateRequired() {
        return getInitializedManager(networkManager)
                .flatMap(NetworkManager::getLucaEndpointsV3)
                .flatMap(LucaEndpointsV3::getSupportedVersionNumber)
                .map(jsonObject -> jsonObject.get("minimumVersion").getAsInt())
                .doOnSuccess(versionNumber -> Timber.d("Minimum supported app version number: %d", versionNumber))
                .map(minimumVersionNumber -> BuildConfig.VERSION_CODE < minimumVersionNumber)
                .onErrorResumeNext(throwable -> {
                    if (NetworkManager.isHttpException(throwable, NetworkManager.HTTP_UPGRADE_REQUIRED)) {
                        return Single.just(true);
                    } else {
                        return Single.error(throwable);
                    }
                })
                .doOnSuccess(isUpdateRequired -> {
                    Timber.v("Update required: %b", isUpdateRequired);
                    if (isUpdateRequired) {
                        showUpdateRequired();
                    }
                })
                .ignoreElement();
    }

    private void showUpdateRequired() {
        showErrorAsDialog(
                new ViewError.Builder(this)
                        .withTitle(R.string.update_required_title)
                        .withDescription(R.string.update_required_description)
                        .withResolveLabel(R.string.action_update)
                        .withResolveAction(Completable.fromAction(() -> {
                            try {
                                openUrl("https://play.google.com/store/apps/details?id=de.culture4life.luca");
                            } catch (ActivityNotFoundException e) {
                                openUrl("https://luca-app.de");
                            }
                        }))
                        .setNotCancelable()
                        .build()
        );
    }

    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities.add(activity);
    }

    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities.remove(activity);
    }

    public void showError(@NonNull ViewError error) {
        boolean isShown = false;

        if (isUiCurrentlyVisible()) {
            try {
                showErrorAsDialog(error);
                isShown = true;
            } catch (UiNotAvailableException e) {
                // Just ignore and try to show as notification.
                // If you want to enforce a dialog is shown then use [showErrorAsDialog] and handle the case.
            }
        }

        if (!isShown && error.canBeShownAsNotification()) {
            showErrorAsNotification(error);
            isShown = true;
        }

        if (!isShown) {
            Timber.w("Unable to show error, UI is not currently visible and error should not be shown as notification: %s", error);
        }
    }

    /**
     * Show error as dialog.
     *
     * @throws UiNotAvailableException Usually happen when app is already closed or not completely started (e.g. still on [SplashActivity]) .
     */
    protected void showErrorAsDialog(@NonNull ViewError error) {
        Activity activity = getActivityContext();
        // SplashActivity is not a good candidate because dialogs becomes instantly dismissed when automatically switched to another activity.
        if (activity == null || activity instanceof SplashActivity) {
            throw new UiNotAvailableException();
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(error.getTitle())
                .setMessage(error.getDescription());

        if (error.isCancelable()) {
            builder.setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel());
        } else {
            builder.setCancelable(false);
        }

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

    protected void showErrorAsNotification(@NonNull ViewError error) {
        NotificationCompat.Builder notificationBuilder = notificationManager.createErrorNotificationBuilder(
                error.getTitle(),
                error.getDescription()
        );
        applicationDisposable.add(notificationManager.showNotification(NOTIFICATION_ID_EVENT, notificationBuilder.build())
                .subscribe());
    }

    @Nullable
    private Activity getActivityContext() {
        if (startedActivities.isEmpty()) {
            return null;
        }
        return new ArrayList<>(startedActivities).get(0);
    }

    public static boolean isGooglePlayServicesAvailable(@NonNull Context context) {
        if (isRunningUnitTests()) {
            return false;
        }
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

    public static boolean isRunningInstrumentationTests() {
        try {
            Class.forName("de.culture4life.luca.LucaInstrumentationTest");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public <ManagerType extends Manager> Single<ManagerType> getInitializedManager(ManagerType manager) {
        return Completable.defer(() -> {
            if (manager.isInitialized()) {
                return Completable.complete();
            } else {
                return manager.initialize(this);
            }
        }).andThen(Single.just(manager));
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public PowManager getPowManager() {
        return powManager;
    }

    public ConsentManager getConsentManager() {
        return consentManager;
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

    public WhatIsNewManager getWhatIsNewManager() {
        return whatIsNewManager;
    }

    public ConnectManager getConnectManager() {
        return connectManager;
    }

    public HealthDepartmentManager getHealthDepartmentManager() {
        return healthDepartmentManager;
    }

    public Maybe<String> getDeepLink() {
        return Maybe.fromCallable(() -> deepLink);
    }

    public void onDeepLinkHandled(@NonNull String url) {
        deepLink = null;
    }

    /**
     * Method to cleanup stuff in emulated environments (see parent method docs!).
     * <p>
     * Usually this one is automatically called when executing tests with robolectric only.
     * <p>
     * For robolectric the onTerminate() is called after each test method to clean up the state because robolectric
     * will create new Application instance. Instrumentation tests reuse the same Application instance for every test
     * method and will never call onTerminate().
     */
    @Override
    public void onTerminate() {
        super.onTerminate();

        // Release all stuff which would otherwise remain in memory and leak between tests methods.
        invalidateAppState();

        // App context is still leaked sometimes and we want to avoid strange side effect through having multiple
        // manager instances in memory.
        preferencesManager = null;
        notificationManager = null;
        locationManager = null;
        networkManager = null;
        geofenceManager = null;
        powManager = null;
        consentManager = null;
        whatIsNewManager = null;
        genuinityManager = null;
        cryptoManager = null;
        registrationManager = null;
        childrenManager = null;
        historyManager = null;
        healthDepartmentManager = null;
        meetingManager = null;
        checkInManager = null;
        dataAccessManager = null;
        documentManager = null;
        connectManager = null;
    }

    static class UiNotAvailableException extends IllegalStateException {
        public UiNotAvailableException() {
            super("ui not available, usually app is closed already or startup not completed yet");
        }
    }
}
