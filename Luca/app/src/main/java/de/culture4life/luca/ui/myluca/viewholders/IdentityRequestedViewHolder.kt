package de.culture4life.luca.ui.myluca.viewholders

import androidx.recyclerview.widget.RecyclerView
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemMyLucaIdentityRequestedBinding
import de.culture4life.luca.ui.myluca.listitems.IdentityRequestedItem
import de.culture4life.luca.util.ClipboardUtil

class IdentityRequestedViewHolder(val binding: ItemMyLucaIdentityRequestedBinding) : RecyclerView.ViewHolder(binding.root) {

    fun show(item: IdentityRequestedItem) {
        binding.tokenTextView.text = item.token

        // extend click behavior onto text view to enlarge hit-box
        binding.tokenTextView.setOnClickListener { copyToken() }
        binding.copyTokenButton.setOnClickListener { copyToken() }
    }

    private fun copyToken() {
        ClipboardUtil.copy(
            binding.root.context,
            binding.root.context.getString(R.string.luca_id_enrollment_token_label),
            binding.tokenTextView.text.toString()
        )
    }

    fun setClickListener(action: () -> Unit) {
        binding.cardView.setOnClickListener { action.invoke() }
    }

    fun setLongClickListener(action: () -> Boolean) {
        binding.cardView.setOnLongClickListener { action.invoke() }
    }
}
