package de.culture4life.luca.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;

import de.culture4life.luca.databinding.FragmentOnboardingInfoBinding;
import de.culture4life.luca.databinding.FragmentOnboardingWelcomeBinding;
import de.culture4life.luca.ui.BaseActivity;
import de.culture4life.luca.ui.registration.RegistrationActivity;
import de.culture4life.luca.ui.terms.UpdatedTermsUtil;
import de.culture4life.luca.util.ViewRequiredUtil;

public class OnboardingActivity extends BaseActivity {

    public static final String WELCOME_SCREEN_SEEN_KEY = "welcome_screen_seen";

    private FragmentOnboardingWelcomeBinding welcomeBinding;
    private FragmentOnboardingInfoBinding infoBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        welcomeBinding = FragmentOnboardingWelcomeBinding.inflate(getLayoutInflater());
        infoBinding = FragmentOnboardingInfoBinding.inflate(getLayoutInflater());

        showWelcomeScreen();
        hideActionBar();
    }

    private void showWelcomeScreen() {
        setContentView(welcomeBinding.getRoot());

        welcomeBinding.primaryActionButton.setOnClickListener(view -> {
            if (welcomeBinding.termsCheckBox.isChecked()) {
                activityDisposable.add(application.getPreferencesManager()
                        .persist(WELCOME_SCREEN_SEEN_KEY, true)
                        .andThen(UpdatedTermsUtil.Companion.markTermsAsAccepted(application))
                        .onErrorComplete()
                        .subscribe(this::showInfoScreen));
            } else {
                showCheckboxErrors();
            }
        });

        welcomeBinding.termsTextView.setMovementMethod(LinkMovementMethod.getInstance());
        welcomeBinding.privacyTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showCheckboxErrors() {
        ViewRequiredUtil.INSTANCE.showCheckBoxRequiredError(welcomeBinding.termsCheckBox, welcomeBinding.termsTextView);
    }

    private void showInfoScreen() {
        setContentView(infoBinding.getRoot());
        infoBinding.primaryActionButton.setOnClickListener(view -> showRegistration());
    }

    private void showRegistration() {
        Intent intent = new Intent(this, RegistrationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

}