package de.culture4life.luca.ui.meeting;

import static de.culture4life.luca.ui.BaseQrCodeViewModel.BARCODE_DATA_KEY;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.culture4life.luca.R;
import de.culture4life.luca.databinding.FragmentMeetingBinding;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.util.AccessibilityServiceUtil;

public class MeetingFragment extends BaseFragment<MeetingViewModel> {

    private FragmentMeetingBinding binding;

    @Nullable
    @Override
    protected ViewBinding getViewBinding() {
        binding = FragmentMeetingBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected Class<MeetingViewModel> getViewModelClass() {
        return MeetingViewModel.class;
    }

    @Override
    protected void initializeViews() {
        super.initializeViews();
        initializeObservers();
        binding.subHeadingTextView.setMovementMethod(new ScrollingMovementMethod());
        binding.meetingGuestsInfoImageView.setOnClickListener(v -> showMeetingMembersInfo());
        binding.slideToActView.setOnSlideCompleteListener(view -> viewModel.onMeetingEndRequested());
        binding.slideToActView.setOnSlideUserFailedListener((view, isOutside) -> {
            if (AccessibilityServiceUtil.isScreenReaderActive(getContext())) {
                viewModel.onMeetingEndRequested();
            } else {
                Toast.makeText(getContext(), R.string.venue_slider_clicked, Toast.LENGTH_SHORT).show();
            }
        });

        if (AccessibilityServiceUtil.isKeyboardConnected(requireContext())) {
            binding.slideToActView.setOnKeyListener((v, keyCode, event) -> {
                if (AccessibilityServiceUtil.isKeyConfirmButton(event)) {
                    viewModel.onMeetingEndRequested();
                }
                return false;
            });
        }
    }

    private void initializeObservers() {
        observe(viewModel.getIsHostingMeeting(), isHostingMeeting -> {
            if (!isHostingMeeting) {
                AccessibilityServiceUtil.speak(getContext(), getString(R.string.meeting_was_ended_hint));
                safeNavigateFromNavController(R.id.action_meetingFragment_to_checkInFragment, viewModel.getBundleLiveData().getValue());
            }
        });
        observe(viewModel.getQrCode(), value -> binding.qrCodeImageView.setImageBitmap(value));
        observe(viewModel.getIsLoading(), loading -> binding.loadingLayout.setVisibility(loading ? View.VISIBLE : View.GONE));
        observe(viewModel.getDuration(), value -> binding.durationTextView.setText(value));
        observe(viewModel.getAllGuests(), value -> {
            long checkedInGuestsCount = value.stream().filter(Guest::isCheckedIn).count();
            binding.guestsCountTextView.setText(String.valueOf(checkedInGuestsCount));
        });
        observe(viewModel.getIsLoading(), loading -> {
            if (!loading) {
                binding.slideToActView.resetSlider();
            }
        });
        observe(viewModel.getBundleLiveData(), this::processBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        Bundle arguments = getArguments();
        if (arguments != null) {
            viewModel.setBundle(arguments);
        }
    }

    @Override
    public void onStop() {
        clearBundle();
        super.onStop();
    }

    private void showMeetingMembersInfo() {
        safeNavigateFromNavController(R.id.action_meetingFragment_to_meetingDetailFragment);
    }

    private void processBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return;
        }

        String barcode = bundle.getString(BARCODE_DATA_KEY);
        if (barcode != null) {
            // is supposed to check-in into different location
            showLocationChangeDialog();
        }
    }

    private void showLocationChangeDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.venue_change_location_title)
                .setMessage(R.string.meeting_change_location_description)
                .setPositiveButton(R.string.action_change, (dialog, which) -> viewModel.changeLocation())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel())
                .setOnCancelListener(dialogInterface -> clearBundle());
        new BaseDialogFragment(builder).show();
    }

    private void clearBundle() {
        Bundle arguments = getArguments();
        if (arguments != null) getArguments().clear();
        viewModel.setBundle(null);
    }

}
