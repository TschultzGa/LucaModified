package de.culture4life.luca.ui.myluca.listitems

import de.culture4life.luca.util.TimeUtil

class IdentityRequestedItem(val token: String) : MyLucaListItem() {
    override val timestamp = TimeUtil.getCurrentMillis()
}
