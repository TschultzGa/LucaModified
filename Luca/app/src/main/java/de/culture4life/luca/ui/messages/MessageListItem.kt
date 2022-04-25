package de.culture4life.luca.ui.messages

import android.net.Uri
import de.culture4life.luca.connect.ConnectMessage
import de.culture4life.luca.dataaccess.AccessedTraceData
import de.culture4life.luca.dataaccess.NotificationTexts
import de.culture4life.luca.whatisnew.WhatIsNewMessage
import java.io.Serializable

sealed class MessageListItem : Serializable {

    abstract val id: String
    abstract val title: String
    abstract val message: String
    abstract val detailedMessage: String
    abstract val timestamp: Long
    abstract val isNew: Boolean

    data class NewsListItem(
        override val id: String,
        override val title: String,
        override val message: String,
        override val detailedMessage: String,
        override val timestamp: Long,
        override val isNew: Boolean,
        val destination: Uri
    ) : MessageListItem() {

        constructor(item: WhatIsNewMessage) : this(
            id = item.id!!,
            title = item.title!!,
            message = item.content!!,
            detailedMessage = item.content!!,
            timestamp = item.timestamp,
            isNew = !item.seen,
            destination = item.destination!!
        )
    }

    data class AccessedDataListItem(
        override val id: String,
        override val title: String,
        override val message: String,
        override val detailedMessage: String,
        override val timestamp: Long,
        override val isNew: Boolean,
        val warningLevel: Int,
        val bannerText: String?,
        val checkInTimestamp: Long,
        val checkOutTimestamp: Long,
        val accessorName: String?,
        val locationName: String
    ) : MessageListItem() {

        constructor(item: AccessedTraceData, texts: NotificationTexts) : this(
            id = item.traceId,
            title = texts.title!!,
            message = texts.shortMessage!!,
            detailedMessage = texts.message!!,
            timestamp = item.accessTimestamp,
            isNew = item.isNew,

            warningLevel = item.warningLevel,
            bannerText = texts.banner,
            checkInTimestamp = item.checkInTimestamp,
            checkOutTimestamp = item.checkOutTimestamp,
            accessorName = item.healthDepartment.name,
            locationName = item.locationName,
        )
    }

    data class LucaConnectListItem(
        override val id: String,
        override val title: String,
        override val message: String,
        override val detailedMessage: String,
        override val timestamp: Long,
        override val isNew: Boolean
    ) : MessageListItem() {

        constructor(item: ConnectMessage) : this(
            id = item.id,
            title = item.title,
            message = item.content,
            detailedMessage = item.content,
            timestamp = item.timestamp,
            isNew = !item.read
        )
    }

    data class MissingConsentItem(
        override val id: String,
        override val title: String,
        override val message: String,
        override val detailedMessage: String,
        override val timestamp: Long,
        override val isNew: Boolean
    ) : MessageListItem()
}
