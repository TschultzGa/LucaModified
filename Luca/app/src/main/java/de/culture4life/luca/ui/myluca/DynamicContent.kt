package de.culture4life.luca.ui.myluca

import androidx.annotation.DrawableRes

data class DynamicContent(val label: String, val content: String? = null, @DrawableRes val endIconDrawable: Int? = null)
