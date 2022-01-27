package de.culture4life.luca.ui.messages

import android.view.View
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentMessageDetailBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.util.getReadableTime

class MessageDetailFragment : BaseFragment<MessageDetailViewModel>() {

    private lateinit var binding: FragmentMessageDetailBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentMessageDetailBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<MessageDetailViewModel> {
        return MessageDetailViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.messageItem) {
            binding.actionBarTitleTextView.text = it.title
            binding.descriptionTextView.text = it.detailedMessage

            when (it) {
                is MessageListItem.AccessedDataListItem -> {
                    setAccessedDataViewsVisibility(View.VISIBLE)
                    binding.healthDepartmentTextView.text = it.accessorName
                    binding.locationTextView.text = it.locationName
                    binding.timeTextView.text = getString(
                        R.string.accessed_data_time,
                        application.getReadableTime(it.checkInTimestamp),
                        application.getReadableTime(it.checkOutTimestamp)
                    )
                }
                is MessageListItem.LucaConnectListItem -> {
                    setAccessedDataViewsVisibility(View.GONE)
                }
            }
            viewModel.onItemSeen(it)
        }
    }

    private fun setAccessedDataViewsVisibility(visibility: Int) {
        binding.healthDepartmentTextView.visibility = visibility
        binding.locationTextView.visibility = visibility
        binding.timeTextView.visibility = visibility
    }

    companion object {
        const val KEY_MESSAGE_LIST_ITEM = "MessageListItem"
    }

}
