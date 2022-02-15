package de.culture4life.luca.ui.messages

import android.view.View
import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentMessagesBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetDialogFragment
import de.culture4life.luca.ui.messages.MessageDetailFragment.Companion.KEY_MESSAGE_LIST_ITEM
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.schedulers.Schedulers

class MessagesFragment : BaseFragment<MessagesViewModel>() {

    private lateinit var binding: FragmentMessagesBinding
    private lateinit var messagesListAdapter: MessagesListAdapter

    override fun getViewBinding(): ViewBinding {
        binding = FragmentMessagesBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<MessagesViewModel> {
        return MessagesViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeMessageItemsViews()
        initializeEmptyStateViews()
        initializeLucaConnectButton()
    }

    override fun onResume() {
        super.onResume()
        viewModel.shouldShowLucaConnectEnrollmentAutomatically()
            .filter { it }
            .doOnSuccess { showLucaConnectEnrollment() }
            .subscribeOn(Schedulers.io())
            .subscribe()
            .addTo(viewDisposable)
    }

    private fun initializeLucaConnectButton() {
        fun updatedConnectButtonVisibility() {
            val enrollmentPossible = viewModel.connectEnrollmentStatus.value == false
                    && viewModel.connectEnrollmentSupportedStatus.value == true
            binding.lucaConnectButton.visibility = if (enrollmentPossible) View.VISIBLE else View.GONE
        }
        observe(viewModel.connectEnrollmentStatus) { updatedConnectButtonVisibility() }
        observe(viewModel.connectEnrollmentSupportedStatus) { updatedConnectButtonVisibility() }
        binding.lucaConnectButton.setOnClickListener { showLucaConnectEnrollment() }
    }

    private fun showLucaConnectEnrollment() {
        LucaConnectBottomSheetDialogFragment.newInstance().show(parentFragmentManager, LucaConnectBottomSheetDialogFragment.TAG)
    }

    private fun initializeMessageItemsViews() {
        with(binding) {
            messagesListAdapter = MessagesListAdapter(requireContext(), messageListView.id)
            messageListView.adapter = messagesListAdapter
            messageListView.setOnItemClickListener { _, _, position, _ ->
                val item = messagesListAdapter.getItem(position - messageListView.headerViewsCount)
                if (item is MessageListItem.NewsListItem) {
                    safeNavigateFromNavController(item.destination)
                } else {
                    val bundle = bundleOf(Pair(KEY_MESSAGE_LIST_ITEM, item))
                    safeNavigateFromNavController(R.id.action_messagesFragment_to_messageDetailFragment, bundle)
                }
            }
            observe(viewModel.messageItems) { allItems -> messagesListAdapter.setMessageItems(allItems) }
        }
    }

    private fun initializeEmptyStateViews() {
        observe(viewModel.messageItems) { items ->
            val emptyStateVisibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            val contentVisibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
            with(binding) {
                emptyStateGroup.visibility = emptyStateVisibility
                messageListView.visibility = contentVisibility
            }
        }
    }
}