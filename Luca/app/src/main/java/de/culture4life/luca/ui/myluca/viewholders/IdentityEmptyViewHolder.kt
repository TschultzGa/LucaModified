package de.culture4life.luca.ui.myluca.viewholders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.culture4life.luca.databinding.ItemMyLucaIdentityEmptyBinding

class IdentityEmptyViewHolder(val binding: ItemMyLucaIdentityEmptyBinding) : RecyclerView.ViewHolder(binding.root) {

    fun setClickListener(onClickListener: View.OnClickListener) {
        binding.root.setOnClickListener(onClickListener)
    }
}
