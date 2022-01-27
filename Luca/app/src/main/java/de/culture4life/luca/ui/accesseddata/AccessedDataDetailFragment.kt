package de.culture4life.luca.ui.accesseddata

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentAccessedDataDetailBinding
import de.culture4life.luca.ui.BaseFragment

class AccessedDataDetailFragment : BaseFragment<AccessedDataDetailViewModel>() {

    private lateinit var binding: FragmentAccessedDataDetailBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentAccessedDataDetailBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<AccessedDataDetailViewModel> {
        return AccessedDataDetailViewModel::class.java
    }

    override fun initializeViews() {
        super.initializeViews()
        observe(viewModel.accessedDataItem) {
            binding.actionBarTitleTextView.text = it.title
            binding.healthDepartmentTextView.text = it.accessorName
            binding.locationTextView.text = it.locationName
            binding.timeTextView.text = it.checkInTimeRange
            binding.descriptionTextView.text = it.detailedMessage
            viewModel.onItemSeen(it)
        }
    }

    companion object {
        const val KEY_ACCESSED_DATA_LIST_ITEM = "AccessedDataListItem"
    }

}
