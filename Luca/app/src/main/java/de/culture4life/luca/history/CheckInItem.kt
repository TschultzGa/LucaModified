package de.culture4life.luca.history

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class CheckInItem(
    @SerializedName("isContactDataMandatory")
    @Expose
    var isContactDataMandatory: Boolean = true
) : HistoryItem() {
    init {
        setType(TYPE_CHECK_IN)
    }
}
