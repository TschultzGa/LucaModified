package de.culture4life.luca.ui.history

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemHistoryBinding

class HistoryListAdapter(context: Context, resource: Int, private val showTimeLine: Boolean) :
    ArrayAdapter<HistoryListItem>(context, resource) {

    private var isInEditMode = false
    var itemClickHandler: ItemClickHandler? = null
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            as LayoutInflater

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
            binding.lineGroup.visibility = View.GONE
            binding.checkbox.visibility = if (item.isContactDataMandatory) View.INVISIBLE else View.VISIBLE
        } else {
            binding.topLineView.visibility = if (showTimeLine && position > 0) View.VISIBLE else View.GONE
            binding.bottomLineView.visibility = if (showTimeLine && position < count - 1) View.VISIBLE else View.GONE

            binding.lineGroup.visibility = View.VISIBLE
            binding.checkbox.visibility = View.GONE
        }

        binding.itemTitleTextView.text = item.title
        binding.itemDescriptionTextView.text = item.description
        binding.itemDescriptionTextView.visibility = if (item.description != null) View.VISIBLE else View.GONE

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
            if (item.additionalTitleDetails != null) {
                setImageResource(item.titleIconResourceId)
                visibility = when {
                    item.accessedTraceData.isNotEmpty() -> {
                        binding.root.setOnClickListener { itemClickHandler?.showAccessedDataDetails(item) }
                        View.VISIBLE
                    }
                    item.isPrivateMeeting -> {
                        binding.root.setOnClickListener { itemClickHandler?.showPrivateMeetingDetails(item) }
                        View.VISIBLE
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

        val isNew = item.accessedTraceData.any { it.isNew }
        val color = ContextCompat.getColor(
            context,
            if (isNew) R.color.highlightColor else android.R.color.white
        )

        binding.itemTitleTextView.setTextColor(color)
        binding.dotView.background.setTint(color)
        binding.itemTitleImageView.setColorFilter(color)
    }

    private fun setupViewForEditMode(binding: ItemHistoryBinding, item: HistoryListItem) {
        val color = ContextCompat.getColor(
            context,
            if (item.isContactDataMandatory) R.color.grey_500_50PC else android.R.color.white
        )
        binding.itemTitleTextView.setTextColor(color)
        binding.itemTitleImageView.setColorFilter(color)
        binding.itemDescriptionTextView.setTextColor(color)
        binding.itemTimeTextView.setTextColor(color)
        binding.checkbox.isChecked = item.isSelectedForDeletion

        if (!item.isContactDataMandatory) {
            // due to reduced checkbox size, click functionality is extended to whole item
            binding.root.setOnClickListener {
                binding.checkbox.isChecked = !binding.checkbox.isChecked
                itemClickHandler?.onItemCheckBoxToggled(item, binding.checkbox.isChecked)
            }
            binding.checkbox.setOnClickListener {
                itemClickHandler?.onItemCheckBoxToggled(item, binding.checkbox.isChecked)
            }
        }
    }

    fun setEditMode(isEditMode: Boolean) {
        isInEditMode = isEditMode
        notifyDataSetChanged()
    }

    interface ItemClickHandler {
        fun showAccessedDataDetails(item: HistoryListItem)
        fun showTraceInformation(item: HistoryListItem)
        fun showPrivateMeetingDetails(item: HistoryListItem)
        fun onItemCheckBoxToggled(item: HistoryListItem, isChecked: Boolean)
    }

}