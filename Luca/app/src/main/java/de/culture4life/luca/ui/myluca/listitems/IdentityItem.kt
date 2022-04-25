package de.culture4life.luca.ui.myluca.listitems

import de.culture4life.luca.idnow.LucaIdData

class IdentityItem @JvmOverloads constructor(var idData: LucaIdData.DecryptedIdData? = null) : MyLucaListItem() {
    override val timestamp: Long
        get() = idData?.validSinceTimestamp ?: -1L
}
