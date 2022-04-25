package de.culture4life.luca.testtools.rules

import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.util.TimeUtil
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FixedTimeRule(
    private var currentDateTime: DateTime = SampleDocuments.referenceDateTime
) : BaseHookingTestRule() {

    constructor(dateTimeString: String) : this(parseDateTime(dateTimeString))

    override fun beforeTest() {
        setCurrentDateTime(currentDateTime)
    }

    override fun afterTest() {
        TimeUtil.clock = Clock.systemUTC()
    }

    fun setCurrentDateTime(dateTimeString: String) {
        setCurrentDateTime(parseDateTime(dateTimeString))
    }

    fun setCurrentDateTime(timestamp: Long) {
        setCurrentDateTime(DateTime(timestamp, DateTimeZone.UTC))
    }

    fun setCurrentDateTime(dateTime: DateTime) {
        currentDateTime = dateTime
        TimeUtil.clock = Clock.fixed(Instant.ofEpochMilli(dateTime.toInstant().millis), ZoneOffset.UTC)
    }

    companion object {
        /**
         * Parse the given string into UTC date time.
         *
         * Supports different format. If no time given, that start of day is used.
         *
         * Following samples have the same result.
         * - 2021-06-15T00:00:00
         * - 2021-06-15T00:00:00Z
         * - 2021-06-15
         */
        fun parseDateTime(datetime: String) = DateTime(datetime, DateTimeZone.UTC)
    }
}
