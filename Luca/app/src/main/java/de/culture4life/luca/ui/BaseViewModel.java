package de.culture4life.luca.ui;

import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_EVENT;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;

import com.tbruyelle.rxpermissions3.Permission;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public abstract class BaseViewModel extends AndroidViewModel {

    public static final String KEY_CAMERA_CONSENT_GIVEN = "camera_consent_given";

    protected final LucaApplication application;
    protected final PreferencesManager preferencesManager;

    protected final CompositeDisposable modelDisposable = new CompositeDisposable();
    protected final MutableLiveData<Boolean> isInitialized = new MutableLiveData<>(false);
    protected final MutableLiveData<Bundle> arguments = new MutableLiveData<>();
    protected final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    protected final MutableLiveData<Set<ViewError>> errors = new MutableLiveData<>(new HashSet<>());
    protected final MutableLiveData<ViewEvent<? extends Set<String>>> requiredPermissions = new MutableLiveData<>();


    @Nullable
    protected NavController navigationController;

    public BaseViewModel(@NonNull Application application) {
        super(application);
        this.application = (LucaApplication) application;
        this.preferencesManager = this.application.getPreferencesManager();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        modelDisposable.dispose();
    }

    @CallSuper
    public Completable initialize() {
        return Single.fromCallable(isInitialized::getValue)
                .filter(alreadyInitialized -> !alreadyInitialized)
                .flatMapCompletable(alreadyInitialized -> updateRequiredPermissions())
                .andThen(preferencesManager.initialize(application))
                .andThen(navigateForDeepLinkIfAvailable())
                .doOnComplete(() -> updateAsSideEffect(isInitialized, true));
    }

    @CallSuper
    public Completable keepDataUpdated() {
        return Completable.never();
    }

    protected final <ValueType> Completable update(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        return Completable.fromAction(() -> updateAsSideEffect(mutableLiveData, value));
    }

    protected final <ValueType> Completable updateIfRequired(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        return Completable.fromAction(() -> updateAsSideEffectIfRequired(mutableLiveData, value));
    }

    protected final <ValueType> void updateAsSideEffect(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        mutableLiveData.postValue(value);
    }

    protected final <ValueType> void updateAsSideEffectIfRequired(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        if (mutableLiveData.getValue() != value) {
            updateAsSideEffect(mutableLiveData, value);
        }
    }

    protected final Completable updateRequiredPermissions() {
        return createRequiredPermissions()
                .distinct()
                .toList()
                .map(HashSet::new)
                .map(ViewEvent::new)
                .flatMapCompletable(permissions -> update(requiredPermissions, permissions));
    }

    @CallSuper
    protected Observable<String> createRequiredPermissions() {
        return Observable.empty();
    }

    protected void addPermissionToRequiredPermissions(String... newlyRequiredPermissions) {
        addPermissionToRequiredPermissions(new HashSet<>(Arrays.asList(newlyRequiredPermissions)));
    }

    protected void addPermissionToRequiredPermissions(Set<String> newlyRequiredPermissions) {
        Timber.v("Added permissions to be requested: %s", newlyRequiredPermissions);
        ViewEvent<? extends Set<String>> permissions = this.requiredPermissions.getValue();
        if (permissions != null && !permissions.hasBeenHandled()) {
            newlyRequiredPermissions.addAll(permissions.getValueAndMarkAsHandled());
        }
        updateAsSideEffect(requiredPermissions, new ViewEvent<>(newlyRequiredPermissions));
    }

    /**
     * Will add the specified error to {@link #errors} when subscribed and remove it when disposed.
     */
    protected final Completable addErrorUntilDisposed(@NonNull ViewError viewError) {
        return Completable.create(emitter -> {
            addError(viewError);
            emitter.setCancellable(() -> removeError(viewError));
        });
    }

    protected ViewError.Builder createErrorBuilder(@NonNull Throwable throwable) {
        return new ViewError.Builder(application)
                .withCause(throwable);
    }

    protected final void addError(@Nullable ViewError viewError) {
        modelDisposable.add(Completable.fromAction(
                () -> {
                    if (viewError == null) {
                        return;
                    }
                    if (!application.isUiCurrentlyVisible() && viewError.canBeShownAsNotification()) {
                        showErrorAsNotification(viewError);
                    } else {
                        synchronized (errors) {
                            Set<ViewError> errorSet = new HashSet<>(errors.getValue());
                            errorSet.add(viewError);
                            errors.setValue(Collections.unmodifiableSet(errorSet));
                        }
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Timber.d("Added error on %s: %s", this, viewError),
                        throwable -> Timber.w("Unable to add error on %s: %s: %s", this, viewError, throwable.toString())
                ));
    }

    protected void showErrorAsNotification(@NonNull ViewError error) {
        LucaNotificationManager notificationManager = application.getNotificationManager();
        NotificationCompat.Builder notificationBuilder;
        if (error.isExpected()) {
            notificationBuilder = notificationManager
                    .createErrorNotificationBuilder(MainActivity.class, error.getTitle(), error.getDescription());
        } else {
            notificationBuilder = notificationManager
                    .createErrorNotificationBuilder(MainActivity.class, error.getTitle(), application.getString(R.string.error_specific_description, error.getDescription()));
        }

        notificationManager.showNotification(NOTIFICATION_ID_EVENT, notificationBuilder.build())
                .subscribe();
    }

    public final void removeError(@Nullable ViewError viewError) {
        modelDisposable.add(Completable.fromAction(
                () -> {
                    if (viewError == null) {
                        return;
                    }
                    synchronized (errors) {
                        Set<ViewError> errorSet = new HashSet<>(errors.getValue());
                        boolean removed = errorSet.remove(viewError);
                        if (removed) {
                            errors.setValue(Collections.unmodifiableSet(errorSet));
                        } else {
                            throw new IllegalStateException("Error was not added before");
                        }
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Timber.d("Removed error on %s: %s", this, viewError),
                        throwable -> Timber.w("Unable to remove error on %s: %s: %s", this, viewError, throwable.toString())
                ));
    }

    public void onErrorShown(@Nullable ViewError viewError) {
        Timber.d("onErrorShown() called on %s with: viewError = [%s]", this, viewError);
        if (viewError != null && viewError.getRemoveWhenShown()) {
            removeError(viewError);
        }
    }

    public void onErrorDismissed(@Nullable ViewError viewError) {
        Timber.d("onErrorDismissed() called on %s with: viewError = [%s]", this, viewError);
        removeError(viewError);
    }

    @CallSuper
    public void onPermissionResult(@NonNull Permission permission) {
        Timber.i("Permission result: %s", permission);
    }

    private Completable navigateForDeepLinkIfAvailable() {
        return application.getDeepLink()
                .flatMap(url -> {
                    if (CheckInManager.isSelfCheckInUrl(url) || MeetingManager.isPrivateMeeting(url)) {
                        return Maybe.just(R.id.checkInFragment);
                    } else if (DocumentManager.isTestResult(url) || DocumentManager.isAppointment(url)) {
                        return Maybe.just(R.id.myLucaFragment);
                    } else {
                        return Maybe.empty();
                    }
                })
                .flatMapCompletable(destinationId -> Completable.fromAction(() -> {
                    if (navigationController != null && !isCurrentDestinationId(destinationId)) {
                        navigationController.navigate(destinationId);
                    }
                }));
    }

    protected boolean isCurrentDestinationId(@IdRes int destinationId) {
        if (navigationController == null) {
            return false;
        }
        NavDestination currentDestination = navigationController.getCurrentDestination();
        return currentDestination != null && currentDestination.getId() == destinationId;
    }

    public LiveData<Bundle> getArguments() {
        return arguments;
    }

    @CallSuper
    public Completable processArguments(@Nullable Bundle arguments) {
        return updateIfRequired(this.arguments, arguments);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public final LiveData<Set<ViewError>> getErrors() {
        return errors;
    }

    public final LiveData<ViewEvent<? extends Set<String>>> getRequiredPermissionsViewEvent() {
        return requiredPermissions;
    }

    public void setNavigationController(@Nullable NavController navigationController) {
        this.navigationController = navigationController;
    }

    protected void export(Single<Uri> uri, Single<String> content) {
        modelDisposable.add(Single.zip(uri, content, this::export)
                .flatMapCompletable(completable -> completable)
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                .doOnError(throwable -> {
                    if (!(throwable instanceof UserCancelledException)) {
                        Timber.w(throwable, "Unable to export data request: %s", throwable.toString());
                        addError(createErrorBuilder(throwable).removeWhenShown().build());
                    }
                })
                .onErrorComplete()
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    private Completable export(Uri uri, String content) {
        return Completable.fromAction(() -> {
            OutputStream stream = application.getContentResolver().openOutputStream(uri);
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        })
                .doOnComplete(() -> Timber.d("Exported:\n%s", content));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public LiveData<Boolean> getIsInitialized() {
        return isInitialized;
    }
}