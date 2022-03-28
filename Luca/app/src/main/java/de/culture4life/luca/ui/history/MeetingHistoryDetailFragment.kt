package de.culture4life.luca.ui.history

import android.graphics.Color
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentMeetingHistoryDetailBinding
import de.culture4life.luca.databinding.ItemGuestBinding
import de.culture4life.luca.ui.BaseFragment

class MeetingHistoryDetailFragment : BaseFragment<MeetingHistoryDetailViewModel>() {

    private lateinit var binding: FragmentMeetingHistoryDetailBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentMeetingHistoryDetailBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<MeetingHistoryDetailViewModel> {
        return MeetingHistoryDetailViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.privateMeetingItem) {
            binding.actionBarTitleTextView.text = it.title
            binding.subtitleTextView.text = it.subtitle
            binding.descriptionTextView.text = it.description
            binding.guestsTitleView.text = it.guestsTitle
            binding.guestsListContainer.removeAllViews()
            it.guests.forEachIndexed { index, name ->
                with(ItemGuestBinding.inflate(layoutInflater)) {
                    guestNumberTextView.apply {
                        text = (index + 1).toString()
                        setTextColor(Color.WHITE)
                    }
                    guestNameTextView.apply {
                        text = name
                        setTextColor(Color.WHITE)
                    }
                    binding.guestsListContainer.addView(root)
                }
            }
        }
    }

    companion object {
        const val KEY_PRIVATE_MEETING_ITEM = "PrivateMeetingItem"
    }
}
