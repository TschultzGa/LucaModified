package de.culture4life.luca.ui.myluca.listitems

import de.culture4life.luca.util.TimeUtil

class IdentityQueuedItem : MyLucaListItem() {
    override val timestamp = TimeUtil.getCurrentMillis()
}
