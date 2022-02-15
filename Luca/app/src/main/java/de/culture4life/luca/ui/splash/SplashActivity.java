package de.culture4life.luca.ui.splash;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import de.culture4life.luca.R;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseActivity;
import de.culture4life.luca.ui.MainActivity;
import de.culture4life.luca.ui.onboarding.OnboardingActivity;
import de.culture4life.luca.ui.registration.RegistrationActivity;
import de.culture4life.luca.ui.terms.UpdatedTermsActivity;
import de.culture4life.luca.ui.terms.UpdatedTermsUtil;
import de.culture4life.luca.ui.whatisnew.WhatIsNewActivity;
import de.culture4life.luca.whatisnew.WhatIsNewManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;


@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Luca_DayNight);
        super.onCreate(savedInstanceState);
        hideActionBar();

        activityDisposable.add(handleDeepLinkIfRequired()
                .andThen(getDestinationActivity())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::navigate));
    }

    private Completable handleDeepLinkIfRequired() {
        return Maybe.fromCallable(() -> getIntent() != null ? getIntent().getData() : null)
                .flatMapCompletable(application::handleDeepLink);
    }

    private Single<Class<?>> getDestinationActivity() {
        return Maybe.mergeArray(
                getOnboardingActivityIfRequired(),
                getRegistrationActivityIfRequired(),
                getTermsActivityIfRequired(),
                getWhatIsNewActivityIfRequired()
        ).first(MainActivity.class);
    }

    private Maybe<Class<?>> getOnboardingActivityIfRequired() {
        return hasSeenWelcomeScreen()
                .flatMapMaybe(seen -> Maybe.fromCallable(() -> seen ? null : OnboardingActivity.class))
                .compose(disableWhatIsNewScreenForTheCurrentVersion());
    }

    private Maybe<Class<?>> getRegistrationActivityIfRequired() {
        return hasCompletedRegistration()
                .flatMapMaybe(registered -> Maybe.fromCallable(() -> registered ? null : RegistrationActivity.class))
                .compose(disableWhatIsNewScreenForTheCurrentVersion());
    }

    private Maybe<Class<?>> getTermsActivityIfRequired() {
        return hasAcceptedTerms()
                .flatMapMaybe(accepted -> Maybe.fromCallable(() -> accepted ? null : UpdatedTermsActivity.class));
    }

    private Maybe<Class<?>> getWhatIsNewActivityIfRequired() {
        return hasSeenWhatIsNew()
                .flatMapMaybe(seen -> Maybe.fromCallable(() -> seen ? null : WhatIsNewActivity.class));
    }

    private Single<Boolean> hasSeenWelcomeScreen() {
        return getInitializedManager(application.getPreferencesManager())
                .flatMap(preferencesManager -> preferencesManager.restoreOrDefault(OnboardingActivity.WELCOME_SCREEN_SEEN_KEY, false))
                .onErrorReturnItem(false);
    }

    private Single<Boolean> hasCompletedRegistration() {
        return getInitializedManager(application.getRegistrationManager())
                .flatMap(RegistrationManager::hasCompletedRegistration)
                .onErrorReturnItem(false);
    }

    private Single<Boolean> hasAcceptedTerms() {
        return UpdatedTermsUtil.Companion.areTermsAccepted(application)
                .onErrorReturnItem(false);
    }

    private Single<Boolean> hasSeenWhatIsNew() {
        return getInitializedManager(application.getWhatIsNewManager())
                .flatMap(WhatIsNewManager::shouldWhatIsNewBeShown)
                .map(show -> !show);
    }

    private <T> MaybeTransformer<T, T> disableWhatIsNewScreenForTheCurrentVersion() {
        return upstream -> upstream.flatMap(value -> getInitializedManager(application.getWhatIsNewManager())
                .flatMapCompletable(WhatIsNewManager::disableWhatIsNewScreenForCurrentVersion)
                .andThen(Maybe.just(value)));
    }

    private void navigate(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

}