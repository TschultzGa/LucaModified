package de.culture4life.luca.ui.history

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemHistoryBinding
import de.culture4life.luca.ui.history.HistoryListItem.*

class HistoryListAdapter(context: Context, resource: Int, private val showTimeLine: Boolean) :
    ArrayAdapter<HistoryListItem>(context, resource) {

    private var isInEditMode = false
    var itemClickHandler: ItemClickHandler? = null
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    fun setHistoryItems(items: List<HistoryListItem>) {
        clear()
        addAll(items)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, container: ViewGroup): View {
        // for smooth scrolling
        val binding = if (convertView == null) {
            ItemHistoryBinding.inflate(layoutInflater)
        } else {
            ItemHistoryBinding.bind(convertView)
        }

        binding.root.setOnClickListener(null)
        val item = getItem(position)!!

        if (isInEditMode) {
            binding.topLineView.visibility = View.GONE
            binding.bottomLineView.visibility = View.GONE
            binding.dotView.visibility = View.GONE
            binding.checkbox.visibility = if (HistoryListItem.isContactDataMandatory(item)) View.INVISIBLE else View.VISIBLE
        } else {
            binding.topLineView.isVisible = showTimeLine && position > 0
            binding.bottomLineView.isVisible = showTimeLine && position < count - 1
            binding.checkbox.visibility = View.GONE
        }

        binding.itemTitleTextView.text = item.title
        when (item) {
            is CheckOutListItem -> {
                binding.itemDescriptionTextView.text = item.description
                binding.itemDescriptionTextView.isVisible = item.description != null
            }
            is MeetingEndedListItem -> {
                binding.itemDescriptionTextView.text = item.description
                binding.itemDescriptionTextView.visibility = View.VISIBLE
            }
            else -> {
                binding.itemDescriptionTextView.visibility = View.GONE
            }
        }

        if (!isInEditMode) {
            setupViewForViewMode(binding, item)
        } else {
            setupViewForEditMode(binding, item)
        }
        binding.itemTimeTextView.text = item.time

        return binding.root
    }

    private fun setupViewForViewMode(binding: ItemHistoryBinding, item: HistoryListItem) {
        with(binding.itemTitleImageView) {
            if (item is CheckOutListItem || item is MeetingEndedListItem || item is DataSharedListItem) {
                visibility = when {
                    item is CheckOutListItem && item.accessedTraceData.isNotEmpty() -> {
                        setImageResource(item.titleIconResourceId)
                        binding.root.setOnClickListener { itemClickHandler?.showAccessedDataDetails(item) }
                        View.VISIBLE
                    }
                    item is MeetingEndedListItem -> {
                        setImageResource(item.titleIconResourceId)
                        binding.root.setOnClickListener { itemClickHandler?.showPrivateMeetingDetails(item) }
                        View.VISIBLE
                    }
                    item is DataSharedListItem -> {
                        setImageResource(item.titleIconResourceId)
                        View.GONE
                    }
                    else -> {
                        View.GONE
                    }
                }
                binding.root.setOnLongClickListener {
                    itemClickHandler?.showTraceInformation(item)
                    true
                }
            } else {
                visibility = View.GONE
                binding.root.setOnLongClickListener(null)
            }
        }

        val isNew = item is CheckOutListItem && item.accessedTraceData.any { it.isNew }
        val color = ContextCompat.getColor(context, if (isNew) R.color.highlightColor else android.R.color.white)

        binding.itemTitleTextView.setTextColor(color)
        binding.dotView.background.setTint(color)
        binding.itemTitleImageView.setColorFilter(color)
    }

    private fun setupViewForEditMode(binding: ItemHistoryBinding, item: HistoryListItem) {
        val isMandatory = HistoryListItem.isContactDataMandatory(item)
        val color = ContextCompat.getColor(context, if (isMandatory) R.color.grey_500_50PC else android.R.color.white)
        binding.itemTitleTextView.setTextColor(color)
        binding.itemTitleImageView.setColorFilter(color)
        binding.itemDescriptionTextView.setTextColor(color)
        binding.itemTimeTextView.setTextColor(color)
        binding.checkbox.isChecked = item.isSelectedForDeletion

        if (!isMandatory) {
            // due to reduced checkbox size, click functionality is extended to whole item
            binding.root.setOnClickListener {
                binding.checkbox.isChecked = !binding.checkbox.isChecked
                itemClickHandler?.onItemCheckBoxToggled(item, binding.checkbox.isChecked)
            }
            binding.checkbox.setOnClickListener { itemClickHandler?.onItemCheckBoxToggled(item, binding.checkbox.isChecked) }
        }
    }

    fun setEditMode(isEditMode: Boolean) {
        isInEditMode = isEditMode
        notifyDataSetChanged()
    }

    interface ItemClickHandler {
        fun showAccessedDataDetails(item: CheckOutListItem)
        fun showTraceInformation(item: HistoryListItem)
        fun showPrivateMeetingDetails(item: MeetingEndedListItem)
        fun onItemCheckBoxToggled(item: HistoryListItem, isChecked: Boolean)
    }
}
