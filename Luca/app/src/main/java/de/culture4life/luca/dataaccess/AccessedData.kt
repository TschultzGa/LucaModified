package de.culture4life.luca.dataaccess

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.archive.ArchivedData

data class AccessedData(
    @Expose @SerializedName("tracingData")
    private var traceData: List<AccessedTraceData> = ArrayList()

) : ArchivedData<AccessedTraceData> {

    override fun getData(): List<AccessedTraceData> {
        return traceData
    }

    override fun setData(data: List<AccessedTraceData>) {
        traceData = data
    }


    override fun toString(): String {
        return "AccessedData{" +
                "traceData=" + traceData +
                '}'
    }
}