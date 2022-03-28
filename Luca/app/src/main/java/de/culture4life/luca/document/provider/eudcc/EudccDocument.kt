package de.culture4life.luca.document.provider.eudcc

import de.culture4life.luca.document.Document.*
import de.culture4life.luca.document.DocumentExpiredException
import de.culture4life.luca.document.DocumentParsingException
import de.culture4life.luca.document.DocumentUtils
import de.culture4life.luca.document.provider.ProvidedDocument
import de.culture4life.luca.util.TimeUtil
import dgca.verifier.app.decoder.CertificateDecodingError
import dgca.verifier.app.decoder.CertificateDecodingResult
import dgca.verifier.app.decoder.model.GreenCertificate
import dgca.verifier.app.decoder.model.Test
import dgca.verifier.app.decoder.model.Vaccination
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Days
import org.joda.time.Duration
import java.util.*

/**
 * Result from a scan or deep link of a EU Digital COVID Certificate (EUDCC)
 */
@ExperimentalUnsignedTypes
class EudccDocument(
    encodedData: String,
    val result: CertificateDecodingResult
) : ProvidedDocument() {

    companion object {
        private const val COVID19_DISEASE = "840539006"
        private val TEST_TYPES = mapOf(
            "LP217198-3" to TYPE_FAST,
            "LP6464-4" to TYPE_PCR
        )
        private val TEST_RESULTS = mapOf(
            Test.TestResult.DETECTED to OUTCOME_POSITIVE,
            Test.TestResult.NOT_DETECTED to OUTCOME_NEGATIVE
        )
        private val VACCINATION_TYPES = mapOf(
            "EU/1/20/1528" to Procedure.Type.VACCINATION_COMIRNATY,
            "EU/1/21/1529" to Procedure.Type.VACCINATION_VAXZEVRIA,
            "EU/1/20/1525" to Procedure.Type.VACCINATION_JANNSEN,
            "EU/1/20/1507" to Procedure.Type.VACCINATION_MODERNA,
            "Sputnik-V" to Procedure.Type.VACCINATION_SPUTNIK_V
        )
    }

    init {
        if (result is CertificateDecodingResult.Success) {
            setupGenericData(encodedData, result)
            setupTestResultData(result)
            setupVaccinationData(result)
            setupRecoveredData(result)
            document.isEudcc = true
        } else {
            val error = result as CertificateDecodingResult.Error
            if (error.error is CertificateDecodingError.GreenCertificateDecodingError) {
                error.error.error?.message?.let {
                    if (it.contains("Expiration not correct")) {
                        throw DocumentExpiredException()
                    }
                }
            }
            throw DocumentParsingException("Could not parse EUDCC: ${error.error}", error.error.error)
        }
    }

    fun getCertificate(): GreenCertificate? {
        return if (result is CertificateDecodingResult.Success) {
            result.greenCertificate
        } else {
            null
        }
    }

    private fun setupGenericData(encodedData: String, result: CertificateDecodingResult.Success) {
        with(document) {
            this.encodedData = encodedData
            with(result.greenCertificate) {
                hashableEncodedData = tests?.getOrNull(0)?.certificateIdentifier
                    ?: vaccinations?.getOrNull(0)?.certificateIdentifier
                    ?: recoveryStatements?.getOrNull(0)?.certificateIdentifier
                hashableEncodedData += vaccinations?.getOrNull(0)?.doseNumber ?: ""
                firstName = person.givenName
                lastName = person.familyName
                document.dateOfBirth = this.dateOfBirth.parseDate()
            }
            id = UUID.nameUUIDFromBytes(hashableEncodedData.toByteArray()).toString()
            importTimestamp = TimeUtil.getCurrentMillis()
        }
    }

    private fun setupTestResultData(result: CertificateDecodingResult.Success) {
        getLatestTestResult(result)?.let { test ->
            verifyCovid19(test.disease)
            with(document) {
                labName = test.certificateIssuer
                labDoctorName = test.testingCentre
                outcome = TEST_RESULTS[test.getTestResultType()] ?: OUTCOME_UNKNOWN
                type = TEST_TYPES[test.typeOfTest] ?: TYPE_UNKNOWN
                testingTimestamp = test.dateTimeOfCollection.parseDate()
                resultTimestamp = test.dateTimeOfTestResult?.parseDate() ?: testingTimestamp
            }
        }
    }

    private fun setupVaccinationData(result: CertificateDecodingResult.Success) {
        val vaccinations = result.greenCertificate.vaccinations ?: return
        with(document) {
            type = TYPE_VACCINATION
            outcome = OUTCOME_PARTIALLY_IMMUNE
            val procedures = ArrayList<Procedure>()
            val initialVaccinationType: Procedure.Type = VACCINATION_TYPES[vaccinations[0].medicinalProduct] ?: Procedure.Type.UNKNOWN
            for (vaccination in vaccinations) {
                verifyCovid19(vaccination.disease)
                val type = VACCINATION_TYPES[vaccination.medicinalProduct] ?: Procedure.Type.UNKNOWN
                val procedure = Procedure(
                    type,
                    vaccination.dateOfVaccination.parseDate(),
                    vaccination.doseNumber,
                    vaccination.totalSeriesOfDoses
                )
                if (vaccination.doseNumber >= vaccination.totalSeriesOfDoses) {
                    val latestRecoveryFromDate = result.greenCertificate
                        .recoveryStatements
                        ?.filter { it.isCertificateNotValidAnymore() == false && it.isCertificateNotValidSoFar() == false }
                        ?.maxByOrNull { DateTime.parse(it.certificateValidFrom).millis }
                        ?.certificateValidFrom
                    validityStartTimestamp = if (isBoosterVaccination(vaccination, initialVaccinationType, latestRecoveryFromDate)) {
                        // Booster vaccinations are effective immediately
                        vaccination.dateOfVaccination.parseDate()
                    } else {
                        vaccination.dateOfVaccination.parseDate(plusMillis = TIME_UNTIL_VACCINATION_IS_VALID)
                    }
                    outcome = OUTCOME_FULLY_IMMUNE
                }
                procedures.add(procedure)
            }
            document.procedures = procedures
            vaccinations.lastOrNull()?.let {
                labName = it.certificateIssuer
                testingTimestamp = it.dateOfVaccination.parseDate()
                resultTimestamp = testingTimestamp
            }
        }
    }

    private fun isBoosterVaccination(vaccination: Vaccination, initialType: Procedure.Type, latestRecoveryDateFrom: String?): Boolean {
        val recoveryDate = if (latestRecoveryDateFrom != null) TimeUtil.zonedDateTimeFromString(latestRecoveryDateFrom) else null
        val vaccinationDate = TimeUtil.zonedDateTimeFromString(vaccination.dateOfVaccination)
        return DocumentUtils.isBoostered(
            latestRecoveryDate = recoveryDate,
            latestVaccinationDate = vaccinationDate,
            initialType = initialType,
            receivedDosesCount = vaccination.doseNumber
        )
    }

    private fun setupRecoveredData(result: CertificateDecodingResult.Success) {
        result.greenCertificate.recoveryStatements?.let { recoveryStatements ->
            with(document) {
                type = TYPE_RECOVERY
                val procedures = ArrayList<Procedure>()
                for (statement in recoveryStatements) {
                    verifyCovid19(statement.disease)
                    val procedure = Procedure(
                        Procedure.Type.RECOVERY,
                        statement.dateOfFirstPositiveTest.parseDate(),
                        1,
                        1 // assuming we only need a single recovery to be valid
                    )
                    procedures.add(procedure)
                }
                document.procedures = procedures
                recoveryStatements.lastOrNull()?.let {
                    labName = it.certificateIssuer
                    testingTimestamp = it.dateOfFirstPositiveTest.parseDate()
                    resultTimestamp = testingTimestamp
                    validityStartTimestamp = it.certificateValidFrom.parseDate()
                    expirationTimestamp = it.certificateValidUntil.parseDate()
                }
                outcome = OUTCOME_FULLY_IMMUNE // recoveries count as fully immune when in the validity time
            }
        }
    }

    private fun getLatestTestResult(result: CertificateDecodingResult.Success) =
        result.greenCertificate.tests?.maxByOrNull { it.dateTimeOfTestResult!!.parseDate() }

    private fun verifyCovid19(disease: String) {
        if (disease != COVID19_DISEASE) throw DocumentParsingException("Can only handle Covid19 disease type, but received $disease")
    }
}

fun String.parseDate(plusDuration: Duration = Days.ZERO.toStandardDuration(), plusMillis: Long = 0L): Long {
    return DateTime(this, DateTimeZone.UTC).plus(plusDuration).plusMillis(plusMillis.toInt()).millis
}
