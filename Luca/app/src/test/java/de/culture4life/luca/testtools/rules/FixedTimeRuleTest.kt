package de.culture4life.luca.testtools.rules

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.util.TimeUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class FixedTimeRuleTest : LucaUnitTest() {

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    val samples = mapOf(
        "2021-06-15T10:30:00" to 1623753000000L,
        "2021-06-15T10:30:00Z" to 1623753000000L,
        "2021-06-15T00:00:00" to 1623715200000L,
        "2021-06-15" to 1623715200000L,
    )

    @Test
    fun parseDateTime() {
        samples.forEach {
            val parseResult = FixedTimeRule.parseDateTime(it.key).millis
            assertThat(parseResult).isEqualTo(it.value)
        }
    }

    @Test
    fun setCurrentDateTimeFromString() {
        samples.forEach {
            fixedTimeRule.setCurrentDateTime(it.key)
            val fixedTime = TimeUtil.clock.millis()
            assertThat(fixedTime).isEqualTo(it.value)
        }
    }

    @Test
    fun setCurrentDateTimeFromTimestamp() {
        samples.forEach {
            fixedTimeRule.setCurrentDateTime(it.value)
            val fixedTime = TimeUtil.clock.millis()
            assertThat(fixedTime).isEqualTo(it.value)
        }
    }
}
