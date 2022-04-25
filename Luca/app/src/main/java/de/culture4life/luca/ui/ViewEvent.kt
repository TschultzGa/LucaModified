package de.culture4life.luca.ui

/**
 * Used as a wrapper for data that is exposed via a [androidx.lifecycle.LiveData] that represents an event.
 */
data class ViewEvent<T> @JvmOverloads constructor(val value: T, var isHandled: Boolean = false) {
    val valueAndMarkAsHandled: T
        get() {
            isHandled = true
            return value
        }

    val isNotHandled: Boolean
        get() = !isHandled
}
