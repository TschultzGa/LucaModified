package de.culture4life.luca.ui.messages

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemMessageBinding
import de.culture4life.luca.util.getReadableDate

class MessagesListAdapter(context: Context, resource: Int) : ArrayAdapter<MessageListItem>(context, resource) {

    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
        as LayoutInflater

    fun setMessageItems(items: List<MessageListItem>) {
        if (shouldUpdateDataSet(items)) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }
    }

    private fun shouldUpdateDataSet(items: List<MessageListItem>): Boolean {
        if (items.size != count) {
            return true
        }
        for (itemIndex in 0 until count) {
            if (!items.contains(getItem(itemIndex))) {
                return true
            }
        }
        return false
    }

    override fun getView(position: Int, convertView: View?, container: ViewGroup): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.item_message, container, false)
        val binding = ItemMessageBinding.bind(view)

        getItem(position)?.let { item ->
            binding.itemTitleTextView.text = item.title
            binding.itemDescriptionTextView.text = item.message
            binding.itemTimeTextView.text = context.getReadableDate(item.timestamp)
            val color = ContextCompat.getColor(
                context,
                if (item.isNew) R.color.highlightColor else android.R.color.white
            )
            binding.dotView.background.setTint(color)
            binding.itemTitleTextView.setTextColor(color)
        }
        return view
    }
}
