package de.culture4life.luca.ui.myluca.viewholders

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemMyLucaIdentityVerifiedBinding
import de.culture4life.luca.databinding.ViewIdCardHorizontalBinding
import de.culture4life.luca.ui.myluca.listitems.IdentityItem
import de.culture4life.luca.util.decodeFromBase64
import de.culture4life.luca.util.getReadableDate

class IdentityVerifiedViewHolder(val binding: ItemMyLucaIdentityVerifiedBinding) : RecyclerView.ViewHolder(binding.root) {

    private lateinit var cardBinding: ViewIdCardHorizontalBinding

    init {
        setupIdCardView()
    }

    private fun setupIdCardView() {
        cardBinding = ViewIdCardHorizontalBinding.inflate(LayoutInflater.from(binding.root.context))
        binding.expandedLayout.addView(cardBinding.root)
        cardBinding.root.doOnPreDraw { cardView ->
            rotateLayout(cardView)

            // Fix parent height doesn't match card view height.
            //  The container still takes [cardView.height] for the height measurement but after rotation the height must be [cardView.width].
            binding.expandedLayout.layoutParams.height = cardView.layoutParams.width
            binding.expandedLayout.requestLayout()
        }
    }

    private fun rotateLayout(cardView: View) {
        val spacingDefault = cardView.resources.getDimensionPixelSize(R.dimen.spacing_default)
        val cardRatio = 520f / 328f
        // Attention when evaluating the [cardView.width]. Currently we have two instances, one for collapsed and one for expanded view state.
        //  For first ViewHolder instance we get [cardView.width == 0] and second ViewHolder instance does have the correct target width. But when we
        //  do a warm start (app background and resume from tasks) with an expanded card view, the ViewHolders are switched. Result is expandedLayout
        //  0 width. Through taking width from parent we ensure to always have the size set in cost of more calculation params (e.g. spacingDefault).
        val cardHeight = cardView.rootView.width - (spacingDefault * 2)
        val cardWidth = cardHeight * cardRatio
        cardView.layoutParams.width = cardWidth.toInt()
        cardView.layoutParams.height = cardHeight
        cardView.translationX = -((cardHeight / 4f) + spacingDefault)
        cardView.translationY = cardHeight / 4f + spacingDefault
        cardView.rotation = 90f
    }

    fun show(item: IdentityItem) {
        binding.expandedLayout.isVisible = item.isExpanded
        binding.collapsedLayout.isVisible = !item.isExpanded
        item.idData?.let { fullIdentity ->
            val name = "${fullIdentity.firstName} ${fullIdentity.lastName}"
            cardBinding.expandedName.text = name
            cardBinding.expandedDate.text = binding.root.context.getReadableDate(fullIdentity.validSinceTimestamp)
            cardBinding.expandedBirthday.text = binding.root.context.getReadableDate(fullIdentity.birthdayTimestamp)
            val decodedImage = fullIdentity.image.decodeFromBase64()
            // TODO Should we do the Bitmap creation earlier in a background thread?
            cardBinding.expandedPhoto.setImageBitmap(BitmapFactory.decodeByteArray(decodedImage, 0, decodedImage.size))
        }
    }

    fun setListeners(
        expandClickListener: View.OnClickListener,
        deleteClickListener: View.OnClickListener,
        iconClickListener: View.OnClickListener
    ) {
        cardBinding.expandedDeleteButton.setOnClickListener(deleteClickListener)
        binding.root.setOnClickListener(expandClickListener)
        cardBinding.expandedIcon.setOnClickListener(iconClickListener)
        binding.icon.setOnClickListener(iconClickListener)
    }
}
