package de.culture4life.luca.ui.myluca.viewholders

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemMyLucaDocumentBinding
import de.culture4life.luca.ui.myluca.DynamicContent
import de.culture4life.luca.ui.myluca.listitems.DocumentItem
import kotlin.math.max

class SingleDocumentViewHolder(val binding: ItemMyLucaDocumentBinding) : RecyclerView.ViewHolder(binding.root) {

    fun show(item: DocumentItem) {
        binding.cardView.setCardBackgroundColor(item.color)
        binding.itemTitleTextView.text = item.title
        binding.itemTitleImageView.setImageResource(item.imageResource)
        binding.qrCodeImageView.setImageBitmap(item.barcode)
        binding.qrCodeImageView.isVisible = item.barcode != null
        binding.providerTextView.text = item.provider
        binding.providerTextView.isVisible = item.barcode != null
        binding.deleteItemButton.text = item.deleteButtonText
        binding.collapseLayout.isVisible = item.isExpanded
        binding.collapseIndicator.rotationX = if (item.isExpanded) 180F else 0F
        setupDynamicContent(item.topContent, binding.topContent)
        setupDynamicContent(item.collapsedContent, binding.collapsedContent)
    }

    fun setListeners(
        expandClickListener: View.OnClickListener? = null,
        deleteClickListener: View.OnClickListener? = null,
        iconClickListener: View.OnClickListener? = null
    ) {
        deleteClickListener?.let { binding.deleteItemButton.setOnClickListener(it) }
        expandClickListener?.let { binding.root.setOnClickListener(it) }
        iconClickListener?.let { binding.itemTitleImageView.setOnClickListener(it) }
    }

    private fun addLabelAndText(
        container: ViewGroup,
        labelTextView: ConstraintLayout?,
        dynamicContent: DynamicContent
    ) {
        var labelAndTextView: ConstraintLayout? = labelTextView
        if (labelAndTextView == null) {
            val layoutInflater = LayoutInflater.from(container.context)
            labelAndTextView = layoutInflater.inflate(
                R.layout.item_my_luca_label_and_text,
                container,
                false
            ) as ConstraintLayout
            container.addView(labelAndTextView)
        }
        val labelView = labelAndTextView.findViewById<TextView>(R.id.labelTextView)
        val textView = labelAndTextView.findViewById<TextView>(R.id.valueTextView)
        val imageView = labelAndTextView.findViewById<ImageView>(R.id.iconImageView)
        labelView.text = dynamicContent.label
        textView.text = dynamicContent.content
        if (dynamicContent.endIconDrawable != null) {
            imageView.isVisible = true
            imageView.setImageResource(dynamicContent.endIconDrawable)
        } else {
            imageView.isVisible = false
            imageView.setImageDrawable(null)
        }
        setConstrainWidth(labelAndTextView, R.id.labelTextView, !TextUtils.isEmpty(dynamicContent.content))
        if (dynamicContent.content.isNullOrBlank()) {
            labelView.setTextAppearance(labelView.context, R.style.TextAppearance_Luca_Body2_Bold)
        }
    }

    private fun setConstrainWidth(
        constraintLayout: ConstraintLayout,
        viewId: Int,
        isConstrained: Boolean,
    ) {
        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.constrainedWidth(viewId, isConstrained)
        set.applyTo(constraintLayout)
    }

    private fun setupDynamicContent(content: List<DynamicContent>, topContent: ViewGroup) {
        for (i in 0 until max(topContent.childCount, content.size)) {
            val labelAndTextView = if (topContent.getChildAt(i) != null) {
                topContent.getChildAt(i) as ConstraintLayout
            } else {
                null
            }
            if (content.size > i) {
                addLabelAndText(topContent, labelAndTextView, content[i])
            } else {
                topContent.removeView(labelAndTextView)
            }
        }
    }
}
