package de.culture4life.luca.ui.registration;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;

import android.animation.ObjectAnimator;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.databinding.FragmentRegistrationAllBinding;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.DefaultTextWatcher;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.util.AccessibilityServiceUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class RegistrationFragment extends BaseFragment<RegistrationViewModel> {

    private static final long DELAY_DURATION = RegistrationViewModel.DEBOUNCE_DURATION;

    private FragmentRegistrationAllBinding binding;

    private static HashMap<Integer, Integer> inputTextIdToHint = new HashMap<>();

    static {
        inputTextIdToHint.put(R.id.firstNameLayout, R.string.registration_missing_first_name_hint);
        inputTextIdToHint.put(R.id.lastNameLayout, R.string.registration_missing_last_name_hint);
        inputTextIdToHint.put(R.id.phoneNumberLayout, R.string.registration_missing_phone_number_hint);
        inputTextIdToHint.put(R.id.streetLayout, R.string.registration_missing_street_hint);
        inputTextIdToHint.put(R.id.houseNumberLayout, R.string.registration_missing_house_number_hint);
        inputTextIdToHint.put(R.id.postalCodeLayout, R.string.registration_missing_postal_code_hint);
        inputTextIdToHint.put(R.id.cityNameLayout, R.string.registration_missing_city_hint);
    }

    private Observer<Boolean> completionObserver;

    @Nullable
    @Override
    protected ViewBinding getViewBinding() {
        binding = FragmentRegistrationAllBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected Class<RegistrationViewModel> getViewModelClass() {
        return RegistrationViewModel.class;
    }

    @Override
    protected void initializeViews() {
        super.initializeViews();
        initializeSharedViews();
        initializeNameViews();
        initializeContactViews();
        initializeAddressViews();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!viewModel.isInEditMode()) {
            viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
        }
    }

    private void initializeSharedViews() {
        observe(viewModel.getProgress(), this::indicateProgress);

        if (viewModel.isInEditMode()) {
            initializeSharedViewsInEditMode();
        } else {
            initializeSharedViewsInRegistrationMode();
        }

        observe(viewModel.getCompleted(), completed -> {
            if (completionObserver != null) {
                completionObserver.onChanged(completed);
            }
        });

        observe(viewModel.getIsLoading(), loading -> {
            binding.registrationActionButton.setEnabled(!loading);

            // indeterminate state can only be changed while invisible
            // see: https://github.com/material-components/material-components-android/issues/1921
            int visibility = binding.registrationProgressIndicator.getVisibility();
            binding.registrationProgressIndicator.setVisibility(View.GONE);
            binding.registrationProgressIndicator.setIndeterminate(loading);
            binding.registrationProgressIndicator.setVisibility(visibility);
        });
    }

    private void initializeSharedViewsInRegistrationMode() {
        binding.registrationHeading.setText(getString(R.string.registration_heading_name));
        if (LucaApplication.IS_USING_STAGING_ENVIRONMENT || BuildConfig.DEBUG) {
            binding.registrationHeading.setOnClickListener(v -> viewModel.useDebugRegistrationData().subscribe());
        }
        binding.registrationActionButton.setOnClickListener(v -> viewDisposable.add(Completable.fromAction(
                () -> {
                    if (isNameStepCompleted()) {
                        viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                        showContactStep();
                    }
                }).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private void initializeSharedViewsInEditMode() {
        binding.registrationHeading.setText(getString(R.string.navigation_contact_data));
        binding.registrationActionButton.setText(R.string.action_update);

        binding.registrationActionButton.setOnClickListener(v -> viewDisposable.add(Completable.mergeArray(
                viewModel.updatePhoneNumberVerificationStatus(),
                viewModel.updateShouldReImportingTestData(),
                viewModel.updateLucaConnectUpdatesRequired()
        ).andThen(viewModel.getPhoneNumberVerificationStatus())
                .delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, Schedulers.io())
                .doOnSubscribe(disposable -> hideKeyboard())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(numberVerified -> {
                    if (!numberVerified && !shouldSkipVerification()) {
                        showCurrentPhoneNumberVerificationStep();
                    } else if (areAllStepsCompleted()) {
                        if (viewModel.isLucaConnectNoticeRequired()) {
                            showLucaConnectNoticeDialog();
                        } else if (viewModel.getShouldReImportTestData()) {
                            showWillDeleteDocumentsDialog();
                        } else {
                            viewModel.onUserDataUpdateRequested();
                        }
                    } else {
                        showMissingInfoDialog();
                    }
                })));

        completionObserver = completed -> {
            if (completed) {
                ((RegistrationActivity) requireActivity()).onEditingContactDataCompleted();
            }
        };
    }

    private void initializeNameViews() {
        bindToLiveData(binding.firstNameLayout, viewModel.getFirstName());
        bindToLiveData(binding.lastNameLayout, viewModel.getLastName());

        if (!viewModel.isInEditMode()) {
            addConfirmationAction(binding.lastNameLayout);
        } else {
            EditText editText = binding.firstNameLayout.getEditText();
            editText.post(() -> editText.setSelection(editText.getText().length()));
        }
    }

    private void initializeContactViews() {
        binding.contactInfoTextView.setVisibility(View.GONE);

        bindToLiveData(binding.phoneNumberLayout, viewModel.getPhoneNumber());

        binding.emailLayout.setRequired(false);
        bindToLiveData(binding.emailLayout, viewModel.getEmail());

        addConfirmationAction(binding.emailLayout);
        if (!viewModel.isInEditMode()) {
            addConfirmationAction(binding.emailLayout);
            binding.phoneNumberLayout.setVisibility(View.GONE);
            binding.emailLayout.setVisibility(View.GONE);
        }
    }

    private void initializeAddressViews() {
        binding.addressInfoTextView.setVisibility(View.GONE);

        bindToLiveData(binding.addressLayout.streetLayout, viewModel.getStreet());
        bindToLiveData(binding.addressLayout.houseNumberLayout, viewModel.getHouseNumber());
        bindToLiveData(binding.postalCodeLayout, viewModel.getPostalCode());
        bindToLiveData(binding.cityNameLayout, viewModel.getCity());
        addConfirmationAction(binding.cityNameLayout);

        if (!viewModel.isInEditMode()) {
            binding.addressLayout.streetLayout.setVisibility(View.GONE);
            binding.addressLayout.houseNumberLayout.setVisibility(View.GONE);
            binding.postalCodeLayout.setVisibility(View.GONE);
            binding.cityNameLayout.setVisibility(View.GONE);
        } else {
            binding.addressLayout.streetLayout.setRequired(true);
            binding.addressLayout.houseNumberLayout.setRequired(true);
            binding.postalCodeLayout.setRequired(true);
            binding.cityNameLayout.setRequired(true);
        }
    }

    private void addConfirmationAction(@NonNull TextInputLayout textInputLayout) {
        Objects.requireNonNull(textInputLayout.getEditText()).setImeOptions(IME_ACTION_DONE);
        Objects.requireNonNull(textInputLayout.getEditText())
                .setOnEditorActionListener((textView, actionId, event) -> binding.registrationActionButton.callOnClick());
    }

    private void bindToLiveData(RegistrationTextInputLayout textInputLayout, LiveData<String> textLiveData) {
        EditText editText = Objects.requireNonNull(textInputLayout.getEditText());
        editText.addTextChangedListener(new DefaultTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                viewModel.onFormValueChanged(textLiveData, editable.toString());
            }
        });
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                textInputLayout.hideError();
            } else {
                viewDisposable.add(Completable.timer(DELAY_DURATION, TimeUnit.MILLISECONDS, Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .andThen(Completable.fromAction(() -> {
                            if (textInputLayout.isEmptyButRequired()) {
                                textInputLayout.setError(getString(R.string.registration_empty_but_required_field_error));
                            } else if (!textInputLayout.isValid() && textInputLayout.isRequired()) {
                                textInputLayout.setError(getString(R.string.registration_invalid_value_field_error));
                            } else {
                                textInputLayout.hideError();
                            }
                        })).subscribe());
            }
        });
        observe(textLiveData, value -> {
            if (!value.trim().equals(editText.getText().toString().trim())) {
                editText.setText(value);
            }
        });
        observe(viewModel.getValidationStatus(textLiveData), textInputLayout::setValid);
    }

    private void indicateProgress(double progress) {
        int percent = (int) Math.max(0, Math.min(100, progress * 100));
        ObjectAnimator.ofInt(binding.registrationProgressIndicator, "progress", percent)
                .setDuration(250)
                .start();
    }

    private void showContactStep() {
        binding.registrationHeading.setText(getString(R.string.registration_heading_contact));

        binding.firstNameLayout.setVisibility(View.GONE);
        binding.lastNameLayout.setVisibility(View.GONE);

        binding.contactInfoTextView.setVisibility(View.VISIBLE);
        binding.phoneNumberLayout.setVisibility(View.VISIBLE);
        binding.emailLayout.setVisibility(View.VISIBLE);
        binding.phoneNumberLayout.requestFocus();

        binding.registrationActionButton.setOnClickListener(v -> viewDisposable.add(viewModel.updatePhoneNumberVerificationStatus()
                .andThen(viewModel.getPhoneNumberVerificationStatus())
                .flatMapCompletable(phoneNumberVerified -> Completable.fromAction(() -> {
                    if (!isContactStepCompleted()) {
                        return;
                    }
                    viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                    if (phoneNumberVerified || shouldSkipVerification()) {
                        showAddressStep();
                    } else {
                        showCurrentPhoneNumberVerificationStep();
                    }
                })).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private void showCurrentPhoneNumberVerificationStep() {
        if (viewModel.getShouldRequestNewVerificationTan().getValue()) {
            long nextPossibleVerificationTanRequestTimestamp = viewModel.getNextPossibleTanRequestTimestamp().getValue();
            if (nextPossibleVerificationTanRequestTimestamp > TimeUtil.getCurrentMillis()) {
                showPhoneNumberRequestTimeoutDialog(nextPossibleVerificationTanRequestTimestamp);
            } else {
                showPhoneNumberVerificationConfirmationDialog();
            }
        } else {
            showPhoneNumberVerificationTanDialog();
        }
    }

    private void showPhoneNumberVerificationConfirmationDialog() {
        String number = viewModel.getFormattedPhoneNumber();
        boolean isMobileNumber = viewModel.isMobilePhoneNumber(number);
        int messageResource = isMobileNumber ? R.string.verification_explanation_sms_description : R.string.verification_explanation_landline_description;
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.verification_explanation_title))
                .setMessage(getString(messageResource, number))
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> requestPhoneNumberVerificationTan())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel()))
                .show();
    }

    private void requestPhoneNumberVerificationTan() {
        viewDisposable.add(viewModel.requestPhoneNumberVerificationTan()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(this::showPhoneNumberVerificationTanDialog)
                .subscribe(
                        () -> Timber.i("Phone number verification TAN sent"),
                        throwable -> Timber.w("Unable to request phone number verification TAN: %s", throwable.toString())
                ));
    }

    private void showPhoneNumberVerificationTanDialog() {
        ViewGroup viewGroup = getActivity().findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_tan, viewGroup, false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setTitle(R.string.verification_enter_tan_title)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // will be overwritten later on to enable dialog to stay open
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                    viewModel.onPhoneNumberVerificationCanceled();
                    dialog.cancel();
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.setCancelable(false);
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> verifyTanDialogInput(alertDialog));

        TextInputEditText tanEditText = dialogView.findViewById(R.id.tanInputEditText);
        tanEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyTanDialogInput(alertDialog);
                return true;
            }
            return false;
        });

        dialogView.findViewById(R.id.infoImageView).setOnClickListener(v -> showTanNotReceivedDialog());
        dialogView.findViewById(R.id.infoTextView).setOnClickListener(v -> showTanNotReceivedDialog());
    }

    private void showMissingInfoDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.registration_missing_info)
                .setMessage(R.string.registration_missing_fields_error_text)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showWillDeleteDocumentsDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.registration_will_delete_tests_title)
                .setMessage(R.string.registration_will_delete_tests_text)
                .setNegativeButton(R.string.action_no, ((dialog, which) -> {
                }))
                .setPositiveButton(R.string.action_yes, (dialog, which) -> viewModel.onUserDataUpdateRequested());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showLucaConnectNoticeDialog() {
        new LucaConnectNotice(viewModel, requireContext())
                .show(() -> viewModel.onUserDataUpdateRequested());
    }

    private void verifyTanDialogInput(@NonNull AlertDialog alertDialog) {
        hideKeyboard();
        completionObserver = completed -> {
            if (completed) {
                ((RegistrationActivity) requireActivity()).onRegistrationCompleted();
            }
        };

        TextInputLayout tanInputLayout = alertDialog.findViewById(R.id.tanInputLayout);
        String verificationTan = tanInputLayout.getEditText().getText().toString();

        if (verificationTan.length() != 6) {
            tanInputLayout.setError(getString(R.string.verification_enter_tan_error));
            return;
        }

        viewDisposable.add(viewModel.verifyTan(verificationTan)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.i("Phone number verified");
                            alertDialog.dismiss();
                            if (viewModel.isInEditMode()) {
                                hideKeyboard();
                                viewModel.onUserDataUpdateRequested();
                            } else {
                                showAddressStep();
                            }
                        },
                        throwable -> {
                            Timber.w("Phone number verification failed: %s", throwable.toString());
                            boolean isForbidden = NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_FORBIDDEN);
                            boolean isBadRequest = NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_BAD_REQUEST);
                            if (isForbidden || isBadRequest) {
                                // TAN was incorrect
                                tanInputLayout.setError(getString(R.string.verification_enter_tan_error));
                            }
                        }
                ));
    }

    private void showTanNotReceivedDialog() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.verification_tan_delayed_title)
                .setMessage(R.string.verification_tan_delayed_description)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel()))
                .show();
    }

    private void showPhoneNumberRequestTimeoutDialog(long nextPossibleTanRequestTimestamp) {
        long duration = nextPossibleTanRequestTimestamp - TimeUtil.getCurrentMillis();
        String readableDuration = TimeUtil.getReadableDurationWithPlural(duration, getContext()).blockingGet();
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.verification_timeout_error_title)
                .setMessage(getString(R.string.verification_timeout_error_description, readableDuration))
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel())
                .setNeutralButton(R.string.verification_timeout_action_use_last, (dialog, which) -> {
                    showPhoneNumberVerificationTanDialog();
                }))
                .show();
    }

    private void showAddressStep() {
        binding.registrationHeading.setText(getString(R.string.registration_heading_address));

        binding.phoneNumberLayout.setVisibility(View.GONE);
        binding.emailLayout.setVisibility(View.GONE);
        binding.contactInfoTextView.setVisibility(View.GONE);

        binding.addressInfoTextView.setVisibility(View.VISIBLE);
        binding.addressLayout.streetLayout.setVisibility(View.VISIBLE);
        binding.addressLayout.houseNumberLayout.setVisibility(View.VISIBLE);
        binding.postalCodeLayout.setVisibility(View.VISIBLE);
        binding.cityNameLayout.setVisibility(View.VISIBLE);
        binding.addressLayout.streetLayout.requestFocus();

        binding.registrationActionButton.setText(getString(R.string.action_finish));
        binding.registrationActionButton.setOnClickListener(view -> viewDisposable.add(Completable.fromAction(
                () -> {
                    if (!isAddressStepCompleted()) {
                        return;
                    }
                    viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                    completionObserver = completed -> {
                        if (completed) {
                            ((RegistrationActivity) requireActivity()).onRegistrationCompleted();
                        }
                    };
                    viewModel.onRegistrationRequested();
                }).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private boolean isNameStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Arrays.asList(binding.firstNameLayout, binding.lastNameLayout));
    }

    private boolean isContactStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Collections.singletonList(binding.phoneNumberLayout));
    }

    private boolean isAddressStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Arrays.asList(
                binding.addressLayout.streetLayout,
                binding.addressLayout.houseNumberLayout,
                binding.postalCodeLayout,
                binding.cityNameLayout
        ));
    }

    private boolean areAllStepsCompleted() {
        return isNameStepCompleted() && isContactStepCompleted() && isAddressStepCompleted();
    }

    private boolean areFieldsValidOrEmptyAndNotRequired(List<RegistrationTextInputLayout> fields) {
        boolean completed = true;
        for (RegistrationTextInputLayout textLayout : fields) {
            if (textLayout.isValidOrEmptyAndNotRequired()) {
                continue;
            }
            if (completed) {
                completed = false;
                textLayout.requestFocus();
            }
            if (textLayout.isEmptyButRequired()) {
                talkbackHintFor(textLayout);
                textLayout.setError(getString(R.string.registration_empty_but_required_field_error));
            } else if (!textLayout.isValid()) {
                textLayout.setError(getString(R.string.registration_invalid_value_field_error));
            }
        }
        return completed;
    }

    private void talkbackHintFor(RegistrationTextInputLayout textLayout) {
        if (inputTextIdToHint.containsKey(textLayout.getId())) {
            AccessibilityServiceUtil.speak(getContext(), getString(inputTextIdToHint.get(textLayout.getId())));
        }
    }

    private boolean shouldSkipVerification() {
        return viewModel.isUsingTestingCredentials() || RegistrationViewModel.SKIP_PHONE_NUMBER_VERIFICATION;
    }

}
