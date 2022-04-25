package de.culture4life.luca.document

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.testtools.rules.FixedTimeRule
import de.culture4life.luca.testtools.samples.SampleDocuments
import org.joda.time.DateTime
import org.junit.Rule
import org.junit.Test

class DocumentManagerDeleteExpiredTest : LucaUnitTest() {

    private val documentManager by lazy { getInitializedManager(application.documentManager) }

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    @Test
    fun `Delete expired pcr negative`() {
        val case = SampleDocuments.ErikaMustermann.EudccPcrNegative()
        val start = case.testingDateTime
        val expiration = case.testingDateTime.plusDays(3)

        fixtureDeleteWhenExpired(case, start, expiration)
    }

    @Test
    fun `Keep expired pcr positive`() {
        val case = SampleDocuments.ErikaMustermann.EudccPcrPositive()
        val start = case.testingDateTime

        fixtureKeepForever(case, start)
    }

    @Test
    fun `Delete expired fast negative`() {
        val case = SampleDocuments.ErikaMustermann.EudccFastNegative()
        val start = case.testingDateTime
        val expiration = case.testingDateTime.plusDays(2)

        fixtureDeleteWhenExpired(case, start, expiration)
    }

    @Test
    fun `Keep expired partially vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val start = case.vaccinationDate

        fixtureKeepForever(case, start)
    }

    @Test
    fun `Keep expired fully vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val start = case.vaccinationDate

        fixtureKeepForever(case, start)
    }

    @Test
    fun `Keep expired booster vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccBoosteredVaccinated()
        val start = case.vaccinationDate

        fixtureKeepForever(case, start)
    }

    @Test
    fun `Keep expired recovery`() {
        val case = SampleDocuments.ErikaMustermann.EudccRecovered()
        val start = case.startDate

        fixtureKeepForever(case, start)
    }

    private fun fixtureDeleteWhenExpired(case: SampleDocuments, start: DateTime, expiration: DateTime) {
        fixedTimeRule.setCurrentDateTime(start)
        val document = givenAddedDocument(case)

        whenDeleteExpiredDocuments()
        assertDocumentStored(document, true)

        fixedTimeRule.setCurrentDateTime(expiration.minusSeconds(1))
        whenDeleteExpiredDocuments()
        assertDocumentStored(document, true)

        fixedTimeRule.setCurrentDateTime(expiration)
        whenDeleteExpiredDocuments()
        assertDocumentStored(document, false)
    }

    private fun fixtureKeepForever(case: SampleDocuments, start: DateTime) {
        fixedTimeRule.setCurrentDateTime(start)
        val document = givenAddedDocument(case)

        whenDeleteExpiredDocuments()
        assertDocumentStored(document, true)

        fixedTimeRule.setCurrentDateTime(start.plusYears(100))
        whenDeleteExpiredDocuments()
        assertDocumentStored(document, true)
    }

    private fun givenAddedDocument(case: SampleDocuments): Document {
        val document = documentManager.parseEncodedDocument(case.qrCodeContent)
            .blockingGet()
            .document

        documentManager.addDocument(document)
            .blockingAwait()

        assertDocumentStored(document, true)

        return document
    }

    private fun assertDocumentStored(document: Document, expected: Boolean) {
        documentManager.getOrRestoreDocuments()
            .toList()
            .test()
            .assertValue { it.contains(document) == expected }
    }

    private fun whenDeleteExpiredDocuments() {
        documentManager.deleteExpiredDocuments()
            .blockingAwait()
    }
}
