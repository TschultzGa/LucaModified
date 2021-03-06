package de.culture4life.luca.util

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.document.Document
import de.culture4life.luca.util.TimeUtil.convertToUnixTimestamp
import de.culture4life.luca.util.TimeUtil.encodeUnixTimestamp
import de.culture4life.luca.util.TimeUtil.getReadableDurationAsTimeWithPlural
import de.culture4life.luca.util.TimeUtil.getReadableDurationWithPlural
import de.culture4life.luca.util.TimeUtil.getReadableTimeDuration
import de.culture4life.luca.util.TimeUtil.roundUnixTimestampDownToMinute
import junit.framework.Assert.assertEquals
import org.joda.time.DateTime
import org.junit.After
import org.junit.Test
import java.nio.ByteBuffer
import java.time.*
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimeUtilTest : LucaUnitTest() {

    @After
    fun after() {
        TimeUtil.clock = Clock.systemUTC()
    }

    @Test
    fun convertToUnixTimestamp() {
        convertToUnixTimestamp(1601481612123L)
            .test()
            .assertValue(1601481612L)
    }

    @Test
    fun roundUnixTimestampDownToMinute() {
        roundUnixTimestampDownToMinute(1601481612L)
            .test()
            .assertValue(1601481600L)
    }

    @Test
    fun encodeUnixTimestamp() {
        encodeUnixTimestamp(1601481600L)
            .map { ByteBuffer.wrap(it).int }
            .test()
            .assertValue(-2136247201)
    }

    @Test
    fun readableDurationWithPlural_evenDurations_expectedEvenAmounts() {
        getReadableDurationWithPlural(TimeUnit.SECONDS.toMillis(0), application)
            .test().assertValue("0 seconds")
        getReadableDurationWithPlural(TimeUnit.SECONDS.toMillis(1), application)
            .test().assertValue("1 second")
        getReadableDurationWithPlural(TimeUnit.SECONDS.toMillis(2), application)
            .test().assertValue("2 seconds")
        getReadableDurationWithPlural(TimeUnit.MINUTES.toMillis(2), application)
            .test().assertValue("2 minutes")
        getReadableDurationWithPlural(TimeUnit.HOURS.toMillis(2), application)
            .test().assertValue("2 hours")
        getReadableDurationWithPlural(TimeUnit.DAYS.toMillis(2), application)
            .test().assertValue("2 days")
    }

    @Test
    fun readableDurationWithPlural_unevenDurations_expectedRoundedAmounts() {
        var duration = TimeUnit.MINUTES.toMillis(2) + TimeUnit.SECONDS.toMillis(30)
        getReadableDurationWithPlural(duration, application).test().assertValue("2 minutes")
        duration = TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(30)
        getReadableDurationWithPlural(duration, application).test().assertValue("2 hours")
        duration = TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(12)
        getReadableDurationWithPlural(duration, application).test().assertValue("2 days")
    }

    // Wed Jul 14 2021 02:00:00 GMT+0200
    @Test
    fun readableDurationWithPlural_vaccinationValidity_expectedDuration() {
        val testingTimestamp = 1626220800000L // Wed Jul 14 2021 02:00:00 GMT+0200
        val validityStartTimestamp = testingTimestamp + Document.TIME_UNTIL_VACCINATION_IS_VALID
        var currentTimestamp = testingTimestamp + TimeUnit.HOURS.toMillis(2)
        var durationUntilValid = validityStartTimestamp - currentTimestamp
        getReadableDurationWithPlural(durationUntilValid, application).test().assertValue("2 weeks")
        currentTimestamp = validityStartTimestamp - TimeUnit.HOURS.toMillis(2)
        durationUntilValid = validityStartTimestamp - currentTimestamp
        getReadableDurationWithPlural(durationUntilValid, application).test().assertValue("2 hours")
    }

    @Test
    fun `Current millis returns fixed timestamp when clock is replaced`() {
        // Given
        val fixedDateTime = LocalDate.now().withYear(1993).withMonth(12).withDayOfMonth(20).atStartOfDay()
        val fixedClock = Clock.fixed(fixedDateTime.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
        TimeUtil.clock = fixedClock

        // When
        val currentMillis = TimeUtil.getCurrentMillis()

        // Then
        val parsedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentMillis), ZoneId.systemDefault())
        assertEquals(fixedDateTime, parsedDateTime)
    }

    @Test
    fun `getReadableTimeDuration returns expected value`() {
        // Given
        val hours = 2L
        val minutes = 35L
        val seconds = 45L
        val expected = String.format(Locale.GERMANY, "%02d:%02d:%02d", hours, minutes, seconds)

        // When
        val duration = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds)

        // Then
        assertEquals(expected, getReadableTimeDuration(duration))
    }

    @Test
    fun `getReadableDurationAsTimeWithPlural returns expected value for single values`() {
        // Given
        val hours = 1L
        val minutes = 1L
        val expected = "$hours hour $minutes minute"

        // When
        val duration = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes)

        // Then
        assertEquals(expected, getReadableDurationAsTimeWithPlural(application, DateTime.now(), DateTime.now().plus(duration)))
    }

    @Test
    fun `getReadableDurationAsTimeWithPlural returns expected value for plural values`() {
        // Given
        val hours = 2L
        val minutes = 35L
        val expected = "$hours hours $minutes minutes"

        // When
        val duration = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes)

        // Then
        assertEquals(expected, getReadableDurationAsTimeWithPlural(application, DateTime.now(), DateTime.now().plus(duration)))
    }

    @Test
    fun `getReadableDurationAsTimeWithPlural returns expected value when only hours given`() {
        // Given
        val hours = 2L
        val expected = "$hours hours"

        // When
        val duration = TimeUnit.HOURS.toMillis(hours)

        // Then
        assertEquals(expected, getReadableDurationAsTimeWithPlural(application, DateTime.now(), DateTime.now().plus(duration)))
    }

    @Test
    fun `getReadableDurationAsTimeWithPlural returns expected value when only minutes given`() {
        // Given
        val minutes = 35L
        val expected = "$minutes minutes"

        // When
        val duration = TimeUnit.MINUTES.toMillis(minutes)

        // Then
        assertEquals(expected, getReadableDurationAsTimeWithPlural(application, DateTime.now(), DateTime.now().plus(duration)))
    }
}
