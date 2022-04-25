package de.culture4life.luca.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import java.util.concurrent.TimeUnit;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.consent.ConsentUiExtension;
import de.culture4life.luca.ui.registration.RegistrationActivity;
import de.culture4life.luca.util.AccessibilityServiceUtil;
import five.star.me.FiveStarMe;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MainActivity extends BaseActivity {

    private NavController navigationController;
    private BottomNavigationView bottomNavigationView;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeNavigation();
        hideActionBar();
        setupKeyboardListener();
        initializeConsentRequests();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.initialize()
                .doOnComplete(() -> {
                    viewModel.setNavigationController(navigationController);
                    viewModel.onNewIntent(getIntent());
                })
                .subscribe();

        Completable.fromAction(() -> FiveStarMe.with(this)
                .setInstallDays(2)
                .setLaunchTimes(7)
                .monitor())
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    private void initializeNavigation() {
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.checkInFragment, R.id.myLucaFragment, R.id.accountFragment, R.id.messagesFragment
        ).build();
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navigationHostFragment);
        navigationController = navHostFragment.getNavController();
        navigationController.setGraph(R.navigation.mobile_navigation);
        NavigationUI.setupActionBarWithNavController(this, navigationController, appBarConfiguration);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setItemIconTintList(null); // don't tint icons, we'll control states changes by ourselves
        NavigationUI.setupWithNavController(bottomNavigationView, navigationController);
        if (AccessibilityServiceUtil.getFontScale(getApplicationContext()) > 1.3) {
            bottomNavigationView.setItemTextAppearanceActive(R.style.ThemeOverlay_Luca_BottomNavigationView_StaticFontSize);
            bottomNavigationView.setItemTextAppearanceInactive(R.style.ThemeOverlay_Luca_BottomNavigationView_StaticFontSize);
        }
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int currentDestinationId = navigationController.getCurrentDestination().getId();
            if (currentDestinationId != item.getItemId()) {
                if (item.getItemId() == R.id.checkInFragment) {
                    int checkInDestinationId = getCheckInScreenDestinationId().blockingGet();
                    if (currentDestinationId != checkInDestinationId) {
                        NavigationUI.onNavDestinationSelected(item, navigationController);
                    }
                } else {
                    NavigationUI.onNavDestinationSelected(item, navigationController);
                }
            }
            return true;
        });

        MenuItem accountMenuItem = bottomNavigationView.getMenu().findItem(R.id.accountFragment);
        MenuItemCompat.setContentDescription(accountMenuItem, getString(R.string.account_tab_title_content_description));

        if (application.isInDarkMode()) {
            // workaround for removing the elevation color overlay
            // https://github.com/material-components/material-components-android/issues/1148
            bottomNavigationView.setElevation(0);
        }
    }

    private void setupKeyboardListener() {
        KeyboardVisibilityEvent.setEventListener(this, isOpen -> {
            bottomNavigationView.setVisibility(isOpen ? View.GONE : View.VISIBLE);
        });
    }

    private void initializeConsentRequests() {
        new ConsentUiExtension(getSupportFragmentManager(), application.getConsentManager(), activityDisposable);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Timber.d("onNewIntent() called with: intent = [%s]", intent);
        super.onNewIntent(intent);
        if (viewModel.isInitialized.getValue()) {
            viewModel.onNewIntent(intent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityDisposable.add(viewModel.keepDataUpdated()
                .doOnSubscribe(disposable -> Timber.d("Keeping data updated for %s", this))
                .doOnError(throwable -> Timber.w(throwable, "Unable to keep data updated for %s", this))
                .retryWhen(errors -> errors.delay(1, TimeUnit.SECONDS))
                .doFinally(() -> Timber.d("Stopping to keep data updated for %s", this))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showRegistrationIfRequired();
        viewModel.getHasNewMessages().observe(this, this::updateMessagesMenuIcon);
    }

    private void showRegistrationIfRequired() {
        activityDisposable.add(application.getRegistrationManager().hasCompletedRegistration()
                .doOnError(throwable -> Timber.w("Unable to check if registration has been completed: %s", throwable.toString()))
                .onErrorReturnItem(false)
                .subscribe(registrationCompleted -> {
                    if (!registrationCompleted) {
                        Intent intent = new Intent(this, RegistrationActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }));
    }

    private Single<Integer> getCheckInScreenDestinationId() {
        Single<Boolean> checkedIn = application.getCheckInManager()
                .initialize(application)
                .andThen(application.getCheckInManager().isCheckedIn());

        Single<Boolean> hostingMeeting = application.getMeetingManager()
                .initialize(application)
                .andThen(application.getMeetingManager().isCurrentlyHostingMeeting());

        return Single.zip(checkedIn, hostingMeeting, (isCheckedIn, isHostingMeeting) -> {
            if (isCheckedIn) {
                return R.id.venueDetailFragment;
            } else if (isHostingMeeting) {
                return R.id.meetingFragment;
            } else {
                return R.id.checkInFragment;
            }
        });
    }

    private void updateMessagesMenuIcon(boolean hasNewMessages) {
        Menu menu = bottomNavigationView.getMenu();
        MenuItem messagesMenuItem = menu.findItem(R.id.messagesFragment);
        if (hasNewMessages) {
            messagesMenuItem.setIcon(R.drawable.ic_navigation_messages_new);
        } else {
            messagesMenuItem.setIcon(R.drawable.ic_navigation_messages);
        }
    }
}
