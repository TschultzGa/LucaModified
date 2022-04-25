package de.culture4life.luca.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.R;
import de.culture4life.luca.dataaccess.AccessedTraceData;
import de.culture4life.luca.databinding.FragmentHistoryBinding;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.util.ClipboardUtil;
import io.reactivex.rxjava3.core.Observable;

public class HistoryFragment extends BaseFragment<HistoryViewModel> {

    public static final String KEY_WARNING_LEVEL_FILTER = "WarningLevel";
    public static final int NO_WARNING_LEVEL_FILTER = -1;

    private HistoryListAdapter historyListAdapter;
    private int warningLevelFilter = NO_WARNING_LEVEL_FILTER;

    private FragmentHistoryBinding binding;

    @Nullable
    @Override
    protected ViewBinding getViewBinding() {
        binding = FragmentHistoryBinding.inflate(getLayoutInflater());
        return binding;
    }

    @Override
    protected Class<HistoryViewModel> getViewModelClass() {
        return HistoryViewModel.class;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            warningLevelFilter = getArguments().getInt(KEY_WARNING_LEVEL_FILTER, NO_WARNING_LEVEL_FILTER);
        }
    }

    @Override
    protected void initializeViews() {
        super.initializeViews();
        initializeHistoryItemsViews();
        initializeShareHistoryViews();
        initializeAccessedDataViews();
        initializeDeleteHistoryViews();
        initializeEmptyStateViews();
    }

    private void initializeDeleteHistoryViews() {
        binding.editHistoryActionBarMenuImageView.setOnClickListener(l -> safeNavigateFromNavController(R.id.action_historyFragment_to_historyEditFragment));
    }

    private void initializeHistoryItemsViews() {
        binding.actionBarTitleTextView.setText(getTitleFor(warningLevelFilter));
        historyListAdapter = new HistoryListAdapter(requireContext(), binding.historyListView.getId(), warningLevelFilter == NO_WARNING_LEVEL_FILTER);
        historyListAdapter.setItemClickHandler(new HistoryListAdapter.ItemClickHandler() {
            @Override
            public void showPrivateMeetingDetails(@NonNull HistoryListItem.MeetingEndedListItem item) {
                Bundle bundle = new Bundle();
                MeetingHistoryItem dataListItem = MeetingHistoryItem.from(requireContext(), item);
                bundle.putSerializable(MeetingHistoryDetailFragment.KEY_PRIVATE_MEETING_ITEM, dataListItem);
                safeNavigateFromNavController(R.id.action_historyFragment_to_meetingHistoryDetailFragment, bundle);
            }

            @Override
            public void showAccessedDataDetails(@NonNull HistoryListItem.CheckOutListItem item) {
                viewModel.onShowAccessedDataRequested(item.getAccessedTraceData(), warningLevelFilter);
            }

            @Override
            public void showTraceInformation(@NonNull HistoryListItem item) {
                if (item instanceof HistoryListItem.CheckOutListItem) {
                    HistoryListItem.CheckOutListItem checkOutListItem = (HistoryListItem.CheckOutListItem) item;
                    showHistoryItemDetailsDialog(item, checkOutListItem.getAdditionalTitleDetails());
                } else if (item instanceof HistoryListItem.MeetingEndedListItem) {
                    HistoryListItem.MeetingEndedListItem meetingEndedListItem = (HistoryListItem.MeetingEndedListItem) item;
                    showHistoryItemDetailsDialog(item, meetingEndedListItem.getAdditionalTitleDetails());
                } else if (item instanceof HistoryListItem.DataSharedListItem) {
                    HistoryListItem.DataSharedListItem dataSharedListItem = (HistoryListItem.DataSharedListItem) item;
                    showHistoryItemDetailsDialog(item, dataSharedListItem.getAdditionalTitleDetails());
                }
            }

            @Override
            public void onItemCheckBoxToggled(@NonNull HistoryListItem item, boolean isChecked) {
            }
        });
        binding.historyListView.setAdapter(historyListAdapter);
        observe(viewModel.getHistoryItems(), items -> historyListAdapter.setHistoryItems(HistoryViewModel.filterHistoryListItems(items, warningLevelFilter)));
    }

    private int getTitleFor(int warningLevelFilter) {
        switch (warningLevelFilter) {
            case 1:
                return R.string.accessed_data_dialog_title;
            case 2:
                return R.string.accessed_data_level_2_title;
            case 3:
                return R.string.accessed_data_level_3_title;
            case 4:
                return R.string.accessed_data_level_4_title;
            default:
                return R.string.navigation_history;
        }
    }

    private void initializeShareHistoryViews() {
        binding.primaryActionButton.setOnClickListener(button -> showShareHistorySelectionDialog());
        observe(viewModel.canHistoryBeShared(), historyCanBeShared -> {
            boolean hideButton = !historyCanBeShared || warningLevelFilter != NO_WARNING_LEVEL_FILTER;
            binding.primaryActionButton.setVisibility(hideButton ? View.GONE : View.VISIBLE);
        });
        observe(viewModel.getTracingTanEvent(), tracingTanEvent -> {
            if (tracingTanEvent.isNotHandled()) {
                showShareHistoryTanDialog(tracingTanEvent.getValueAndMarkAsHandled());
            }
        });
    }

    private void initializeAccessedDataViews() {
        observe(viewModel.getNewAccessedData(), accessedDataEvent -> {
            if (accessedDataEvent.isNotHandled()) {
                showAccessedDataDialog(accessedDataEvent.getValueAndMarkAsHandled());
            }
        });
    }

    private void initializeEmptyStateViews() {
        binding.emptyDescriptionTextView.setText(getString(R.string.history_empty_description, HistoryManager.KEEP_DATA_DAYS));

        observe(viewModel.getHistoryItems(), items -> {
            int emptyStateVisibility = items.isEmpty() ? View.VISIBLE : View.GONE;
            int contentVisibility = !items.isEmpty() ? View.VISIBLE : View.GONE;
            binding.emptyStateGroup.setVisibility(emptyStateVisibility);
            binding.historyContentGroup.setVisibility(contentVisibility);
        });
    }

    protected void showHistoryItemDetailsDialog(@NonNull HistoryListItem item, String additionalDetails) {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getTitle())
                .setMessage(additionalDetails)
                .setPositiveButton(R.string.action_ok, (dialogInterface, i) -> dialogInterface.cancel())
                .setNeutralButton(R.string.action_copy, (dialogInterface, i) -> {
                    String content = item.getTitle() + "\n" + item.getTime() + "\n" + additionalDetails;
                    ClipboardUtil.copy(requireContext(), "luca", content);
                }))
                .show();
    }

    private void showShareHistorySelectionDialog() {
        ViewGroup viewGroup = requireActivity().findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_days, viewGroup, false);

        int maximumDays = (int) TimeUnit.MILLISECONDS.toDays(HistoryManager.SHARE_DATA_DURATION);
        String[] items = new String[maximumDays];
        Observable.range(1, maximumDays)
                .map(String::valueOf)
                .toList().blockingGet().toArray(items);
        TextView message = dialogView.findViewById(R.id.messageTextView);
        message.setText(getString(R.string.history_share_selection_description, HistoryManager.KEEP_DATA_DAYS));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_select_days, items);
        AutoCompleteTextView autoCompleteTextView = dialogView.findViewById(R.id.dayInputAutoCompleteTextView);
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.setText(String.valueOf(maximumDays), false);

        new BaseDialogFragment(new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setTitle(getString(R.string.history_share_selection_title))
                .setPositiveButton(R.string.history_share_selection_action, (dialogInterface, i) -> {
                    int selectedDays = Integer.parseInt(autoCompleteTextView.getText().toString());
                    showShareHistoryConfirmationDialog(selectedDays);
                })
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> dialogInterface.cancel()))
                .show();
    }

    private void showShareHistoryConfirmationDialog(int days) {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.history_share_confirmation_title))
                .setMessage(getFormattedString(R.string.history_share_confirmation_description, days))
                .setPositiveButton(R.string.history_share_confirmation_action, (dialogInterface, i) -> viewModel.onShareHistoryRequested(days))
                .setNegativeButton(R.string.action_cancel, (dialogInterface, i) -> dialogInterface.cancel()))
                .show();
    }

    private void showShareHistoryTanDialog(@NonNull String tracingTan) {
        BaseDialogFragment dialogFragment = new BaseDialogFragment(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.history_share_tan_title))
                .setMessage(getString(R.string.history_share_tan_description, tracingTan))
                .setPositiveButton(R.string.action_ok, (dialogInterface, i) -> dialogInterface.dismiss()));
        dialogFragment.setCancelable(false);
        dialogFragment.show();
    }

    private void showAccessedDataDialog(@NonNull List<AccessedTraceData> accessedTraceDataList) {
        if (accessedTraceDataList.isEmpty() || !hasWarningLevel1(accessedTraceDataList)) {
            return;
        }
        BaseDialogFragment dialogFragment = new BaseDialogFragment(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.accessed_data_dialog_title)
                .setMessage(R.string.accessed_data_dialog_description)
                .setPositiveButton(R.string.accessed_data_dialog_action_show, (dialog, which) -> viewModel.onShowAccessedDataRequested())
                .setNeutralButton(R.string.accessed_data_dialog_action_dismiss, (dialogInterface, i) -> dialogInterface.cancel()));

        dialogFragment.setCancelable(false);
        dialogFragment.show();
    }

    private boolean hasWarningLevel1(@NonNull List<AccessedTraceData> accessedTraceDataList) {
        boolean hasWarningLevel1 = false;
        for (AccessedTraceData data : accessedTraceDataList) {
            if (data.getWarningLevel() == 1) {
                hasWarningLevel1 = true;
                break;
            }
        }

        return hasWarningLevel1;
    }
}
