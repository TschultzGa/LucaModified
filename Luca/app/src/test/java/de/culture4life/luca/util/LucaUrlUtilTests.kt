package de.culture4life.luca.util

import de.culture4life.luca.LucaUnitTest
import junit.framework.Assert.assertEquals
import org.junit.Test

class LucaUrlUtilTests : LucaUnitTest() {

    @Test
    fun `isLucaUrl returns correct value for given Url string`() {
        val testData = mapOf(
            "luca://luca-app.de/webapp/something" to true,
            "luca://luca-app.de/webapp" to true,
            "https://luca-app.de/webapp/something" to true,
            "http://luca-app.de/webapp" to true,
            "http://luca-app.de/webapp/something" to true,
            "http://luca-app.de/webapp" to true,
            "http://luc-app.de/webapp" to false,
            "http://luca-app.de" to false,
            "luca-app.de/webapp" to false,
        )

        testData.forEach {
            assertEquals("${it.value} is wrong result for ${it.key}", it.value, LucaUrlUtil.isLucaUrl(it.key))
        }
    }

    @Test
    fun `isAppointment returns correct value for given Url string`() {
        val testData = mapOf(
            "https://app.luca-app.de/webapp/appointment/e4e3c...#e30" to true,
            "https://app.luca-app.de/webapp" to false,
            "https://www.google.de" to false
        )

        testData.forEach {
            assertEquals("${it.value} is wrong result for ${it.key}", it.value, LucaUrlUtil.isAppointment(it.key))
        }
    }

    @Test
    fun `isPrivateMeeting returns correct value for given Url string`() {
        val testData = mapOf(
            "https://app.luca-app.de/webapp/meeting/e4e3c...#e30" to true,
            "https://app.luca-app.de/webapp" to false,
            "https://www.google.de" to false
        )

        testData.forEach {
            assertEquals("${it.value} is wrong result for ${it.key}", it.value, LucaUrlUtil.isPrivateMeeting(it.key))
        }
    }

    @Test
    fun `isTestResult returns correct value for given Url string`() {
        val testData = mapOf(
            "https://app.luca-app.de/webapp/testresult/#eyJ0eXAi..." to true,
            "https://app.luca-app.de/webapp/testresult/eyJ0eXAi..." to false,
            "https://app.luca-app.de/webapp/meeting/e4e3c...#e30" to false,
            "https://app.luca-app.de/webapp/" to false,
            "https://www.google.com" to false,
            "https://www.google.com/webapp/testresult/#eyJ0eXAi..." to false,
            "" to false
        )

        testData.forEach {
            assertEquals("${it.value} is wrong result for ${it.key}", it.value, LucaUrlUtil.isTestResult(it.key))
        }
    }

    @Test
    fun `isSelfCheckIn returns correct value for given Url string`() {
        val testData = mapOf(
            "https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f" to true,
            "https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f#e30" to true,
            "https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f#e30/CWA1/CiRmY2E..." to true,
            "https://app.luca-app.de/webapp/" to false,
            "https://app.luca-app.de/webapp/setup" to false,
            "https://app.luca-app.de/webapp/testresult/#eyJ0eXAi..." to false,
        )

        testData.forEach {
            assertEquals("${it.value} is wrong result for ${it.key}", it.value, LucaUrlUtil.isSelfCheckIn(it.key))
        }
    }
}
