package de.culture4life.luca.util

import android.content.Context
import de.culture4life.luca.R
import de.culture4life.luca.util.TimeUtil.GERMAN_TIMEZONE
import io.reactivex.rxjava3.core.Single
import org.joda.time.*
import org.joda.time.format.DateTimeFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.util.concurrent.TimeUnit

object TimeUtil {

    /**
     * The clock used by [currentMillis]
     */
    @JvmStatic
    var clock: Clock = Clock.systemUTC()

    /**
     * Returns the current millisecond timestamp depending on the set [clock]
     *
     * By changing the [clock] this can for example always return a fixed timestamp in tests.
     */
    @JvmStatic
    fun getCurrentMillis(): Long = java.time.Instant.now(clock).toEpochMilli()

    @JvmStatic
    fun getCurrentUnixTimestamp(): Single<Long> {
        return convertToUnixTimestamp(getCurrentMillis())
    }

    @JvmStatic
    fun convertFromUnixTimestamp(unixTimestamp: Long): Single<Long> {
        return Single.just(unixTimestamp)
            .map { TimeUnit.SECONDS.toMillis(it) }
    }

    @JvmStatic
    fun convertToUnixTimestamp(timestampInMilliseconds: Long): Single<Long> {
        return Single.just(timestampInMilliseconds)
            .map { TimeUnit.MILLISECONDS.toSeconds(it) }
    }

    @JvmStatic
    fun roundUnixTimestampDownToMinute(unixTimestamp: Long): Single<Long> {
        return Single.just(unixTimestamp)
            .map { TimeUnit.SECONDS.toMinutes(it) }
            .map { TimeUnit.MINUTES.toSeconds(it) }
    }

    @JvmStatic
    fun encodeUnixTimestamp(unixTimestamp: Long): Single<ByteArray> {
        return Single.fromCallable {
            val byteBuffer = ByteBuffer.allocate(4)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.putInt(unixTimestamp.toInt())
            byteBuffer.array()
        }
    }

    @JvmStatic
    fun decodeUnixTimestamp(encodedUnixTimestamp: ByteArray): Single<Long> {
        return Single.fromCallable {
            val byteBuffer = ByteBuffer.wrap(encodedUnixTimestamp)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.int.toLong()
        }
    }

    @JvmStatic
    fun getStartOfCurrentDayTimestamp(): Single<Long> {
        return Single.fromCallable { TimeUtil.getCurrentMillis() }
            .flatMap { getStartOfDayTimestamp(it) }
    }

    @JvmStatic
    fun getStartOfDayTimestamp(timestamp: Long): Single<Long> {
        return Single.just(timestamp - timestamp % (60 * 60 * 24 * 1000))
    }

    @JvmStatic
    fun getReadableTime(context: Context, timestamp: Long): String {
        return context.getReadableTime(timestamp)
    }

    /**
     * Calculate duration how a human would do it by adding a specific period instead of fixed timestamps.
     * e.g. 15.01 + 6 Month is 15.07, result will be != TimeUnit.DAYS.toMillis(30 * 6)
     *
     * @return The duration in ms between [startTimestamp] and [startTimestamp] + [addedPeriod]
     */
    @JvmStatic
    fun calculateHumanLikeDuration(startTimestamp: Long, addedPeriod: Period): Long {
        return Instant
            .ofEpochMilli(startTimestamp)
            .toDateTime(DateTimeZone.UTC)
            .plus(addedPeriod)
            .millis - startTimestamp
    }

    fun zonedDateTimeFromString(stringToParse: String) = DateTime.parse(stringToParse).withZone(DateTimeZone.getDefault())

    fun zonedDateTimeFromTimestamp(timestamp: Long) = DateTime.now(DateTimeZone.getDefault()).withMillis(timestamp)

    @JvmStatic
    fun getReadableDateTimeDifference(context: Context, start: DateTime, end: DateTime = DateTime.now(DateTimeZone.getDefault())): String {
        val seconds = Seconds.secondsBetween(start, end).seconds
        val minutes = Minutes.minutesBetween(start, end).minutes
        val hours = Hours.hoursBetween(start, end).hours
        val days = Days.daysBetween(start, end).days
        val weeks = Weeks.weeksBetween(start, end).weeks
        val months = Months.monthsBetween(start, end).months

        val before = when {
            months >= 1 -> months to R.plurals.time_plural_months
            weeks >= 1 -> weeks to R.plurals.time_plural_weeks
            days >= 1 -> days to R.plurals.time_plural_days
            hours >= 1 -> hours to R.plurals.time_plural_hours
            minutes >= 1 -> minutes to R.plurals.time_plural_minutes
            else -> seconds to R.plurals.time_plural_seconds
        }

        return context.resources.getQuantityString(before.second, before.first, before.first)
    }

    @JvmStatic
    fun getReadableDurationWithPlural(duration: Long, context: Context): Single<String> {
        return Single.just(getReadableDateTimeDifference(context, DateTime.now(), DateTime.now().plus(duration)))
    }

    val GERMAN_TIMEZONE: DateTimeZone = DateTimeZone.forID("Europe/Berlin")
}

fun DateTime.isAfterNowMinusPeriod(period: ReadablePeriod): Boolean {
    val now = DateTime.now(DateTimeZone.getDefault())
    return isAfter(now.minus(period))
}

fun Long.toDateTime(timeZone: DateTimeZone = DateTimeZone.getDefault()): DateTime {
    return Instant.ofEpochMilli(this).toDateTime(timeZone)
}

fun Long.toUnixTimestamp(): Long {
    return TimeUtil.convertToUnixTimestamp(this).blockingGet()
}

fun Long.fromUnixTimestamp(): Long {
    return TimeUtil.convertFromUnixTimestamp(this).blockingGet()
}

fun Context.getReadableTime(timestamp: Long, timeZone: DateTimeZone = GERMAN_TIMEZONE): String {
    return DateTimeFormat.forPattern(getString(R.string.time_format)).print(timestamp.toDateTime(timeZone))
}

fun Context.getReadableDate(timestamp: Long, timeZone: DateTimeZone = GERMAN_TIMEZONE): String {
    return DateTimeFormat.forPattern(getString(R.string.date_format)).print(timestamp.toDateTime(timeZone))
}
