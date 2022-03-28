package de.culture4life.luca.document

import de.culture4life.luca.document.Document.*
import de.culture4life.luca.document.Document.Procedure.Type.VACCINATION_COMIRNATY
import de.culture4life.luca.document.Document.Procedure.Type.VACCINATION_JANNSEN
import de.culture4life.luca.util.TimeUtil
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.joda.time.Months
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class DocumentUtilsTest {

    @Test
    fun `Is boostered returns false if given list contains no vaccinations`() {
        // When
        val result = DocumentUtils.isBoostered(listOf(), listOf())

        // Then
        assertFalse(result)
    }

    @Test
    fun `Is boostered returns false if given list only contains first Biontech vaccination`() {
        // Given
        val firstBiontechVacc = getValidVaccination()
        whenever(firstBiontechVacc.procedures).thenReturn(arrayListOf(Procedure(VACCINATION_COMIRNATY, Instant.now().millis, 1, 1)))

        // When
        val result = DocumentUtils.isBoostered(listOf(firstBiontechVacc), listOf())

        // Then
        assertFalse(result)
    }

    @Test
    fun `Is boostered returns false if given list only contains two Biontech vaccinations`() {
        // Given
        val now = TimeUtil.getCurrentMillis()
        val firstBiontechVacc = getValidVaccination(now - 1)
        whenever(firstBiontechVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 1, 1, 1)
            )
        )

        val secondBiontechVacc = getValidVaccination(now)
        whenever(secondBiontechVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 1, 1, 2),
                Procedure(VACCINATION_COMIRNATY, now, 2, 2),
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(firstBiontechVacc, secondBiontechVacc), listOf())

        // Then
        assertFalse(result)
    }

    @Test
    fun `Is boostered returns true if given list contains two Biontech vaccinations + booster vaccination`() {
        // Given
        val now = TimeUtil.getCurrentMillis()
        val firstBiontechVacc = getValidVaccination(now - 2)
        whenever(firstBiontechVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now, 1, 1)
            )
        )

        val secondBiontechVacc = getValidVaccination(now - 1)
        whenever(secondBiontechVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 1, 1, 2),
                Procedure(VACCINATION_COMIRNATY, now, 2, 2),
            )
        )

        val boosterVacc = getValidVaccination(now)
        whenever(boosterVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 2, 1, 3),
                Procedure(VACCINATION_COMIRNATY, now - 1, 2, 3),
                Procedure(VACCINATION_COMIRNATY, now, 3, 3),
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(firstBiontechVacc, secondBiontechVacc, boosterVacc), listOf())

        // Then
        assertTrue(result)
    }

    @Test
    fun `Is boostered returns false when there is no vaccination in the last nine months`() {
        // Given
        val document = getValidVaccination(DateTime.now(DateTimeZone.getDefault()).minus(Months.TEN).millis)
        whenever(document.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, TimeUtil.getCurrentMillis(), 1, 3),
                Procedure(VACCINATION_COMIRNATY, TimeUtil.getCurrentMillis(), 2, 3),
                Procedure(VACCINATION_COMIRNATY, TimeUtil.getCurrentMillis(), 3, 3)
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(document), listOf())

        // Then
        assertFalse(result)
    }

    @Test
    fun `Is boostered returns true when person has one Johnnson vaccination and one other vaccination`() {
        // Given
        val now = TimeUtil.getCurrentMillis()
        val firstVacc = getValidVaccination(now - 1)
        whenever(firstVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_JANNSEN, now, 1, 1)
            )
        )

        val secondVacc = getValidVaccination(now)
        whenever(secondVacc.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_JANNSEN, now - 1, 1, 1),
                Procedure(VACCINATION_COMIRNATY, now, 2, 1),
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(firstVacc, secondVacc), listOf())

        // Then
        assertTrue(result)
    }

    @Test
    fun `Is boostered returns true when person has recovered in the last 6 months and has any vaccination`() {
        // Given
        val recovery = getValidRecovery(DateTime.now(DateTimeZone.getDefault()).minus(Months.FIVE).millis)
        val vaccination = getValidVaccination()

        // When
        val result = DocumentUtils.isBoostered(listOf(vaccination), listOf(recovery))

        // Then
        assertTrue(result)
    }

    @Test
    fun `Is boostered returns true when person has recovered in the last 12 months and has two vaccinations`() {
        // Given
        val now = TimeUtil.getCurrentMillis()
        val recovery = getValidRecovery(DateTime.now(DateTimeZone.getDefault()).minus(Months.TEN).millis)
        val vaccination = getValidVaccination(now)
        whenever(vaccination.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 100, 1, 2),
                Procedure(VACCINATION_COMIRNATY, now, 2, 2),
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(vaccination), listOf(recovery))

        // Then
        assertTrue(result)
    }

    @Test
    fun `Is boostered returns false if vaccinated and recovered but recovery is too long ago`() {
        // Given
        val now = TimeUtil.getCurrentMillis()
        val recovery = getValidRecovery(DateTime.now(DateTimeZone.getDefault()).minus(Months.months(13)).millis)
        val vaccination = getValidVaccination(now)
        whenever(vaccination.procedures).thenReturn(
            arrayListOf(
                Procedure(VACCINATION_COMIRNATY, now - 100, 1, 2),
                Procedure(VACCINATION_COMIRNATY, now, 2, 2),
            )
        )

        // When
        val result = DocumentUtils.isBoostered(listOf(vaccination), listOf(recovery))

        // Then
        assertFalse(result)
    }

    private fun getValidRecovery(timestamp: Long = TimeUtil.getCurrentMillis()): Document = mock {
        whenever(it.type).thenReturn(TYPE_RECOVERY)
        whenever(it.isValidRecovery).thenReturn(true)
        whenever(it.testingTimestamp).thenReturn(timestamp)
    }

    private fun getValidVaccination(timestamp: Long = TimeUtil.getCurrentMillis()): Document = mock {
        whenever(it.type).thenReturn(TYPE_VACCINATION)
        whenever(it.isValidVaccination).thenReturn(true)
        whenever(it.isVerified).thenReturn(true)
        whenever(it.testingTimestamp).thenReturn(timestamp)
        whenever(it.procedures).thenReturn(arrayListOf<Procedure>(Procedure(VACCINATION_COMIRNATY, timestamp, 1, 1)))
    }
}
