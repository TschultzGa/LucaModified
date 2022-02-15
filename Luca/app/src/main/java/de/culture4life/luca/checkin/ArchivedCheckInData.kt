package de.culture4life.luca.checkin

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

/**
 * Model of previous check-ins, stored locally and [removed after two weeks][CheckInManager.deleteOldArchivedCheckInData].
 */
class ArchivedCheckInData(
    @Expose
    @SerializedName("check-ins")
    var checkIns: List<CheckInData> = ArrayList()
) : ArchivedData<CheckInData> {

    override fun getData(): List<CheckInData> {
        return checkIns
    }

    override fun setData(data: List<CheckInData>) {
        checkIns = data
    }

    override fun toString(): String {
        return "ArchivedCheckInData{" +
                "checkIns=" + checkIns +
                '}'
    }

}
