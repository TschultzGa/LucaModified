package de.culture4life.luca.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import timber.log.Timber

fun ImageView.safelySetImageResource(@DrawableRes resId: Int) {
    try {
        this.setImageResource(resId)
    } catch (exception: Exception) {
        Timber.e("Exception setting image resource: ${exception.message}")
    }
}