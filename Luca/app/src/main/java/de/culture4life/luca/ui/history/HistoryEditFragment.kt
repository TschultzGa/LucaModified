package de.culture4life.luca.ui.history

import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentHistoryBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.history.HistoryListAdapter.ItemClickHandler
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

open class HistoryEditFragment : BaseFragment<HistoryViewModel>() {

    private lateinit var historyListAdapter: HistoryListAdapter

    private lateinit var binding: FragmentHistoryBinding

    private var isListPrepared = false

    override fun getViewBinding(): ViewBinding {
        binding = FragmentHistoryBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<HistoryViewModel> = HistoryViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        initializeEditHistoryViews()
        initializeHistoryItemsViews()
    }

    private fun initializeEditHistoryViews() {
        binding.actionBarTitleTextView.text = ""
        binding.editHistoryActionBarMenuImageView.setImageResource(R.drawable.ic_bin)
        binding.editHistoryActionBarMenuImageView.contentDescription = getString(R.string.history_clear_content_description)
        binding.editHistoryActionBarMenuImageView.setOnClickListener { showClearHistoryConfirmationDialog() }
        binding.primaryActionButton.isVisible = true
        binding.primaryActionButton.isEnabled = false
        binding.primaryActionButton.text = getString(R.string.action_delete_selected)
        binding.primaryActionButton.setOnClickListener { showDeletedSelectedHistoryItemsDialog() }
    }

    private fun initializeHistoryItemsViews() {
        historyListAdapter = HistoryListAdapter(requireContext(), binding.historyListView.id, true)
        historyListAdapter.setEditMode(true)
        historyListAdapter.itemClickHandler = object : ItemClickHandler {

            override fun showAccessedDataDetails(item: HistoryListItem.CheckOutListItem) {}

            override fun showTraceInformation(item: HistoryListItem) {}

            override fun showPrivateMeetingDetails(item: HistoryListItem.MeetingEndedListItem) {}

            override fun onItemCheckBoxToggled(item: HistoryListItem, isChecked: Boolean) {
                item.isSelectedForDeletion = isChecked
                val count: Long = viewModel.getHistoryItems().value!!.stream()
                    .filter(HistoryListItem::isSelectedForDeletion)
                    .count()
                binding.primaryActionButton.isEnabled = count > 0
            }
        }
        binding.historyListView.adapter = historyListAdapter

        observe(viewModel.getHistoryItems()) { historyListItems ->
            if (!isListPrepared) {
                viewModel.getHistoryItems().value!!.stream()
                    .forEach { item -> item.isSelectedForDeletion = false }
                isListPrepared = true
            }
            if (historyListItems.isEmpty()) {
                navigationController.popBackStack()
            } else {
                historyListAdapter.setHistoryItems(historyListItems)
            }
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_description)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    application.historyManager.clearItems()
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            { Timber.i("History cleared") },
                            { Timber.w("Unable to clear history: %s", it.toString()) }
                        )
                }
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
        )
            .show()
    }

    private fun showDeletedSelectedHistoryItemsDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_delete_selected_title)
                .setMessage(R.string.history_delete_selected_description)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.onDeleteSelectedHistoryListItemsRequested()
                    binding.primaryActionButton.isEnabled = false
                }
                .setNeutralButton(R.string.action_cancel) { dialogInterface, _ -> dialogInterface.cancel() }
        )
            .show()
    }
}
