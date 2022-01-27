package de.culture4life.luca.util

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import de.culture4life.luca.R

object ViewRequiredUtil {

    fun showCheckBoxRequiredError(checkBox: MaterialCheckBox, textView: TextView) {
        val normalTint = checkBox.buttonTintList!!.defaultColor
        showCheckBoxRequired(checkBox, textView, normalTint, true)

        checkBox.setOnClickListener {
            if (checkBox.isChecked) {
                showCheckBoxRequired(checkBox, textView, normalTint, false)
            }
        }
    }

    private fun showCheckBoxRequired(checkBox: MaterialCheckBox, textView: TextView, normalTint: Int, hasError: Boolean) {
        val errorTint = MaterialColors.getColor(checkBox, R.attr.colorWarning)
        val errorDrawable = ContextCompat.getDrawable(checkBox.context, R.drawable.ic_error_outline)!!.also {
            it.setTint(errorTint)
        }
        checkBox.buttonTintList = ColorStateList.valueOf(if (hasError) errorTint else normalTint)
        textView.setCompoundDrawablesWithIntrinsicBounds(null, null, if (hasError) errorDrawable else null, null)
    }
}