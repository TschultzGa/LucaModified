package de.culture4life.luca.ui.children

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import de.culture4life.luca.util.AccessibilityServiceUtil

abstract class KeyboardInputTrackingAdapter<VH>(context: Context, resource: Int) : ArrayAdapter<VH>(context, resource) {

    enum class KeyboardInputEvents {
        ENTER_PRESSED,
        LEFT_PRESSED,
        RIGHT_PRESSED,
        UPDATE_SELECTED_ITEM_POSITION
    }

    fun setListener(listView: ListView, action: (keyboardInputEvent: KeyboardInputEvents, position: Int) -> Unit) {
        listView.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                val selectedItemPosition = listView.selectedItemPosition
                when {
                    KeyEvent.ACTION_UP == event.action && AccessibilityServiceUtil.isKeyConfirmButton(event) &&
                        event.flags and KeyEvent.FLAG_LONG_PRESS != KeyEvent.FLAG_LONG_PRESS -> {
                        action(KeyboardInputEvents.ENTER_PRESSED, selectedItemPosition)
                        return true
                    }
                    event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        event.flags and KeyEvent.FLAG_LONG_PRESS != KeyEvent.FLAG_LONG_PRESS -> {
                        action(KeyboardInputEvents.RIGHT_PRESSED, selectedItemPosition)
                        return true
                    }
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                        event.flags and KeyEvent.FLAG_LONG_PRESS != KeyEvent.FLAG_LONG_PRESS -> {
                        action(KeyboardInputEvents.LEFT_PRESSED, selectedItemPosition)
                        return true
                    }
                    KeyEvent.ACTION_UP == event.action -> {
                        action(KeyboardInputEvents.UPDATE_SELECTED_ITEM_POSITION, listView.selectedItemPosition)
                    }
                }
                return false
            }
        })
    }
}
