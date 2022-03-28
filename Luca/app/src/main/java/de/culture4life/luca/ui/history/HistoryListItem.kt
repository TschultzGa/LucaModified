package de.culture4life.luca.ui.history

import android.content.Context
import androidx.annotation.DrawableRes
import de.culture4life.luca.R
import de.culture4life.luca.dataaccess.AccessedTraceData
import de.culture4life.luca.dataaccess.DataAccessManager
import de.culture4life.luca.history.*
import de.culture4life.luca.util.TimeUtil

sealed class HistoryListItem {

    abstract var timestamp: Long
    abstract var time: String
    abstract var title: String
    abstract var relatedId: String
    var isSelectedForDeletion: Boolean = false

    class CheckInListItem(context: Context, checkInItem: CheckInItem) : HistoryListItem() {
        override var timestamp: Long = checkInItem.timestamp
        override var time: String = context.getString(R.string.history_time, TimeUtil.getReadableTime(context, checkInItem.timestamp))
        override var title: String = checkInItem.displayName
        override var relatedId: String = checkInItem.relatedId

        var isContactDataMandatory: Boolean = checkInItem.isContactDataMandatory
    }

    class CheckOutListItem(context: Context, checkOutItem: CheckOutItem, dataAccessManager: DataAccessManager) : HistoryListItem() {
        override var timestamp: Long = checkOutItem.timestamp
        override var time: String = context.getString(R.string.history_time, TimeUtil.getReadableTime(context, checkOutItem.timestamp))
        override var title: String = checkOutItem.displayName
        override var relatedId: String = checkOutItem.relatedId

        var description: String? = null
        var accessedTraceData: List<AccessedTraceData> = dataAccessManager.getPreviouslyAccessedTraceData(checkOutItem.relatedId)
            .toList()
            .blockingGet()
        var additionalTitleDetails: String = context.getString(R.string.history_check_out_details, checkOutItem.relatedId)

        @DrawableRes
        var titleIconResourceId: Int = if (accessedTraceData.isNotEmpty()) R.drawable.ic_eye else R.drawable.ic_information_outline
        var isContactDataMandatory: Boolean = false

        init {
            val children = checkOutItem.children
            if (children != null && children.isNotEmpty()) {
                val currentDescription = description
                var builder = StringBuilder()
                if (currentDescription != null) {
                    builder = builder.append(currentDescription)
                        .append(System.lineSeparator())
                }
                val childrenCsv = HistoryManager.createCsv(children)
                builder = builder.append(context.getString(R.string.history_children_title, childrenCsv))
                description = builder.toString()
            }
        }

        fun containsWarningLevel(warningLevel: Int): Boolean {
            return accessedTraceData.any {
                it.warningLevel == warningLevel
            }
        }
    }

    class MeetingStartedListItem(context: Context, historyItem: HistoryItem) : HistoryListItem() {
        override var timestamp: Long = historyItem.timestamp
        override var time: String = context.getString(R.string.history_time, TimeUtil.getReadableTime(context, historyItem.timestamp))
        override var title: String = context.getString(R.string.history_meeting_started_title)
        override var relatedId: String = historyItem.relatedId

        var isContactDataMandatory: Boolean = false
    }

    class MeetingEndedListItem(context: Context, meetingEndedItem: MeetingEndedItem) : HistoryListItem() {
        override var timestamp: Long = meetingEndedItem.timestamp
        override var time: String = context.getString(R.string.history_time, TimeUtil.getReadableTime(context, meetingEndedItem.timestamp))
        override var title: String = context.getString(R.string.history_meeting_ended_title)
        override var relatedId: String = meetingEndedItem.relatedId

        var isContactDataMandatory: Boolean = false

        var additionalTitleDetails = context.getString(R.string.history_check_out_details, relatedId)
        var titleIconResourceId = R.drawable.ic_information_outline

        var description: String
        val guests: List<String>

        init {
            if (meetingEndedItem.guests.isEmpty()) {
                description = context.getString(R.string.history_meeting_empty_description)
                guests = emptyList()
            } else {
                val guestCsv = HistoryManager.createCsv(meetingEndedItem.guests)
                description = context.getString(R.string.history_meeting_not_empty_description, guestCsv)
                guests = meetingEndedItem.guests
            }
        }
    }

    class DataSharedListItem(context: Context, dataSharedItem: DataSharedItem) : HistoryListItem() {
        override var timestamp: Long = dataSharedItem.timestamp
        override var time: String = context.getString(R.string.history_time, TimeUtil.getReadableTime(context, dataSharedItem.timestamp))
        override var title: String = context.getString(R.string.history_data_shared_title)
        override var relatedId: String = dataSharedItem.relatedId

        var additionalTitleDetails = context.getString(R.string.history_data_shared_description, dataSharedItem.days)
        var titleIconResourceId = R.drawable.ic_information_outline
    }

    companion object {

        fun canHandle(item: HistoryItem) = item.type in listOf(
            HistoryItem.TYPE_CHECK_IN,
            HistoryItem.TYPE_CHECK_OUT,
            HistoryItem.TYPE_MEETING_STARTED,
            HistoryItem.TYPE_MEETING_ENDED,
            HistoryItem.TYPE_CONTACT_DATA_REQUEST
        )

        fun isContactDataMandatory(historyListItem: HistoryListItem): Boolean {
            return historyListItem is CheckInListItem && historyListItem.isContactDataMandatory || historyListItem is CheckOutListItem && historyListItem.isContactDataMandatory
        }
    }
}
