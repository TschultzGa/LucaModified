package de.culture4life.luca.ui.myluca.listitems

import de.culture4life.luca.util.TimeUtil

class IdentityEmptyItem : MyLucaListItem() {
    override val timestamp = TimeUtil.getCurrentMillis()
}
