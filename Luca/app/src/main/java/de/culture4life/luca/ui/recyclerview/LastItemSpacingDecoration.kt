package de.culture4life.luca.ui.recyclerview

import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds [paddingRes] to the bottom of the last item of the RecyclerView
 */
class LastItemSpacingDecoration(@DimenRes private val paddingRes: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val isLastItem = parent.getChildAdapterPosition(view) == state.itemCount - 1
        if (isLastItem) {
            val padding = view.context.resources.getDimensionPixelSize(paddingRes)
            outRect.bottom = padding
        }
    }
}
