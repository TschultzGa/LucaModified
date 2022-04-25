package de.culture4life.luca.ui.myluca

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.DrawableMarginSpan
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import de.culture4life.luca.R

class BlurryIdentityNameText @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, defaultStyle: Int = 0) :
    AppCompatTextView(context, attributeSet, defaultStyle) {

    init {
        val defaultSpace = resources.getDimensionPixelSize(R.dimen.spacing_default)
        val spannable = SpannableString(resources.getString(R.string.luca_id_card_name_blurry_placeholder))
        // We need to set the padding with this hacky trick to ensure that the blur filter is not cut off (would be with normal padding/margin)
        spannable.setSpan(
            DrawableMarginSpan(ColorDrawable(Color.TRANSPARENT), defaultSpace),
            0,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = spannable
        paint.maskFilter = BlurMaskFilter(textSize / 3, BlurMaskFilter.Blur.NORMAL)

        // On some (mostly old/slow performance?) android devices the blur effect glitches. This doesn't happen when enforcing software rendering.
        //  Seen when there are overlapping layouts which are switched between gone/visible.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }
}
