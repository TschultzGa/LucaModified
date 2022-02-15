package de.culture4life.luca.document

import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.isAfterNowMinusPeriod
import org.joda.time.DateTime
import org.joda.time.Months

object DocumentUtils {

    @JvmStatic
    fun isBoostered(validVaccinations: List<Document>, validRecoveries: List<Document>): Boolean {
        val latestRecoveryDate = validRecoveries.maxByOrNull { it.testingTimestamp }?.let { TimeUtil.zonedDateTimeFromTimestamp(it.testingTimestamp) }
        val latestVaccination = validVaccinations.maxByOrNull { it.testingTimestamp } ?: return false
        val latestVaccinationDate = TimeUtil.zonedDateTimeFromTimestamp(latestVaccination.testingTimestamp)
        val initialType = latestVaccination.procedures.minByOrNull { it.timestamp }?.type ?: Document.Procedure.Type.UNKNOWN
        val receivedDosesCount = latestVaccination.procedures.maxOf { it.doseNumber }
        return isBoostered(latestRecoveryDate, latestVaccinationDate, initialType, receivedDosesCount)
    }

    @JvmStatic
    fun isBoostered(
        latestRecoveryDate: DateTime?,
        latestVaccinationDate: DateTime,
        initialType: Document.Procedure.Type,
        receivedDosesCount: Int
    ): Boolean {
        val hasRecoveryNewerThanTwelveMonths = latestRecoveryDate?.isAfterNowMinusPeriod(Months.TWELVE) == true
        val hasRecoveryNewerThanSixMonths = latestRecoveryDate?.isAfterNowMinusPeriod(Months.SIX) == true
        val hasVaccinationNewerThanNineMonths = latestVaccinationDate.isAfterNowMinusPeriod(Months.NINE)
        return when {
            !hasVaccinationNewerThanNineMonths -> false
            hasRecoveryNewerThanSixMonths -> receivedDosesCount >= 1
            hasRecoveryNewerThanTwelveMonths -> receivedDosesCount >= 2
            initialType != Document.Procedure.Type.UNKNOWN -> receivedDosesCount > VACCINATION_DOSES[initialType]!!
            else -> receivedDosesCount >= 3
        }
    }

    private val VACCINATION_DOSES = mapOf(
        Document.Procedure.Type.VACCINATION_COMIRNATY to 2,
        Document.Procedure.Type.VACCINATION_VAXZEVRIA to 2,
        Document.Procedure.Type.VACCINATION_JANNSEN to 1,
        Document.Procedure.Type.VACCINATION_MODERNA to 2,
        Document.Procedure.Type.VACCINATION_SPUTNIK_V to 2
    )
}