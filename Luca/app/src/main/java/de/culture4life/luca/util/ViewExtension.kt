package de.culture4life.luca.util

import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.Group

fun Group.setOnClickListeners(listener: (view: View) -> Unit) {
    referencedIds.forEach {
        val v = rootView.findViewById<View>(it)
        v.setOnClickListener { listener(v) }
    }
}

fun SwitchCompat.setCheckedImmediately(checked: Boolean?) {
    isChecked = checked == true
    jumpDrawablesToCurrentState()
}
