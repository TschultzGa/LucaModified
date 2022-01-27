package de.culture4life.luca.ui.compound

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ViewSwitchWithDescriptionBinding

class DescriptionSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    val binding = ViewSwitchWithDescriptionBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        context.withStyledAttributes(attrs, R.styleable.DescriptionSwitchView) {
            showIcon(getBoolean(R.styleable.DescriptionSwitchView_showInfoIcon, false))
            setInfoText(getString(R.styleable.DescriptionSwitchView_infoText))
        }
    }

    fun showIcon(show: Boolean) {
        binding.infoImageView.isVisible = show
    }

    fun setInfoText(text: String?) {
        binding.infoTextView.text = text
    }

    fun setInfoText(@StringRes resId: Int) {
        binding.infoTextView.setText(resId)
    }

    fun setSwitchOnClickListener(listener: OnClickListener) {
        binding.toggle.setOnClickListener { listener.onClick(binding.toggle) }
    }

    fun isChecked(): Boolean = binding.toggle.isChecked

    fun setChecked(checked: Boolean) {
        binding.toggle.isChecked = checked
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        binding.toggle.setOnCheckedChangeListener { buttonView, isChecked -> listener.onCheckedChanged(buttonView, isChecked) }
    }

    fun setInfoTextOnClickListener(listener: OnClickListener) {
        binding.infoTextView.setOnClickListener { listener.onClick(binding.infoTextView) }
        binding.infoImageView.setOnClickListener { listener.onClick(binding.infoImageView) }
    }
}