package de.culture4life.luca.ui.compound

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemAccountBinding

class AccountItemView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attributeSet, defStyleAttr) {

    val binding = ItemAccountBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        context.withStyledAttributes(attributeSet, R.styleable.AccountItemView) {
            setText(getString(R.styleable.AccountItemView_text))
            setStartIconImageResource(getResourceId(R.styleable.AccountItemView_startIconSrc, R.drawable.ic_information_outline))
            setEndIconImageResource(getResourceId(R.styleable.AccountItemView_endIconSrc, R.drawable.ic_arrow))
            showStartIcon(getBoolean(R.styleable.AccountItemView_showStartIcon, true))
            showEndIcon(getBoolean(R.styleable.AccountItemView_showEndIcon, true))
            showSeparator(getBoolean(R.styleable.AccountItemView_showSeparator, true))
        }
    }

    fun setText(text: String?) {
        binding.itemTextView.text = text
    }

    fun setText(@StringRes resId: Int) {
        binding.itemTextView.setText(resId)
    }

    fun setStartIconImageResource(@DrawableRes resId: Int) {
        binding.itemStartIconImageView.setImageResource(resId)
    }

    fun setEndIconImageResource(@DrawableRes resId: Int) {
        binding.itemEndIconImageView.setImageResource(resId)
    }

    fun showStartIcon(show: Boolean) {
        binding.itemStartIconImageView.isVisible = show
    }

    fun showEndIcon(show: Boolean) {
        binding.itemEndIconImageView.isVisible = show
    }

    fun showSeparator(show: Boolean) {
        binding.itemSeparatorView.isVisible = show
    }

}