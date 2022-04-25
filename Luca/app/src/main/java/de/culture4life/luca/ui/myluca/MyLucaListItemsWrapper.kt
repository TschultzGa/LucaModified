package de.culture4life.luca.ui.myluca

import de.culture4life.luca.R
import de.culture4life.luca.ui.myluca.listitems.DocumentItem
import de.culture4life.luca.ui.myluca.listitems.MyLucaListItem

data class MyLucaListItemsWrapper(
    val items: List<MyLucaListItem>,
    val timeStamp: Long,
    val sectionHeader: String? = null,
    var isChildSection: Boolean = false
) {

    constructor(sectionHeader: String) : this(
        listOf(),
        0,
        sectionHeader,
        true
    )

    constructor(item: MyLucaListItem, isChildSection: Boolean = false) : this(
        listOf(item),
        item.timestamp,
        isChildSection = isChildSection
    )

    constructor(items: List<MyLucaListItem>, isChildSection: Boolean) : this(
        items.sortedBy { it.timestamp },
        if (items.isEmpty()) 0 else items.sortedByDescending { it.timestamp }[0].timestamp,
        isChildSection = isChildSection
    )

    fun documentItems(): List<DocumentItem> = items.filterIsInstance<DocumentItem>()

    fun hasMultipleItems(): Boolean = items.size > 1

    fun isSectionHeader() = sectionHeader != null

    fun sectionDrawable(): Int {
        return if (isChildSection) R.drawable.ic_child else 0
    }
}
