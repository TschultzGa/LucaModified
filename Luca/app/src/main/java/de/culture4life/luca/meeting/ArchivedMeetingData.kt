package de.culture4life.luca.meeting

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

class ArchivedMeetingData : ArchivedData<MeetingData> {

    @Expose
    @SerializedName("meetings")
    var meetings: List<MeetingData>

    constructor() {
        meetings = ArrayList()
    }

    constructor(archivedMeetings: List<MeetingData>) {
        meetings = archivedMeetings
    }

    override fun getData(): List<MeetingData> {
        return meetings
    }

    override fun setData(data: List<MeetingData>) {
        meetings = data
    }

    override fun toString(): String {
        return "ArchivedMeetingData{" +
            "meetings=" + meetings +
            '}'
    }
}
