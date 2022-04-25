package de.culture4life.luca.ui.myluca.viewholders

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.RecyclerView

/**
 * Workaround the effect that margin/padding is ignored from the item layout root.
 *
 * Looks like this effect happens in combination with MaterialCardView but not with a LinearLayout as item root.
 */
class MarginItemDecoration(context: Context, @DimenRes spacingId: Int) : RecyclerView.ItemDecoration() {

    private val spacing = context.resources.getDimensionPixelSize(spacingId)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.offset(spacing, spacing)
    }
}
