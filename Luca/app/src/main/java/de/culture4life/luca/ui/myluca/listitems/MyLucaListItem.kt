package de.culture4life.luca.ui.myluca.listitems

abstract class MyLucaListItem {
    abstract val timestamp: Long
    var isExpanded: Boolean = false
    fun toggleExpanded() {
        isExpanded = !isExpanded
    }
}
