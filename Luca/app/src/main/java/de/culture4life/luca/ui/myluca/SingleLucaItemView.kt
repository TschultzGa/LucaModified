package de.culture4life.luca.ui.myluca

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import de.culture4life.luca.databinding.ItemMyLucaBinding

class SingleLucaItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    val binding = ItemMyLucaBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        this.layoutParams = params
    }

}

