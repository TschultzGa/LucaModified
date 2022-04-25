package de.culture4life.luca.document

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.testtools.rules.FixedTimeRule
import de.culture4life.luca.testtools.samples.SampleDocuments
import org.joda.time.DateTime
import org.junit.Rule
import org.junit.Test

class DocumentManagerAddExpiredTest : LucaUnitTest() {

    private val documentManager by lazy { getInitializedManager(application.documentManager) }

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    @Test
    fun `Decline expired pcr negative`() {
        val case = SampleDocuments.ErikaMustermann.EudccPcrNegative()
        val start = case.testingDateTime

        fixtureDeclineExpired(case, start)
    }

    @Test
    fun `Allow expired pcr positive`() {
        val case = SampleDocuments.ErikaMustermann.EudccPcrPositive()
        val start = case.testingDateTime

        fixtureAllowExpired(case, start)
    }

    @Test
    fun `Decline expired fast negative`() {
        val case = SampleDocuments.ErikaMustermann.EudccFastNegative()
        val start = case.testingDateTime

        fixtureDeclineExpired(case, start)
    }

    @Test
    fun `Allow expired partially vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val start = case.vaccinationDate

        fixtureAllowExpired(case, start)
    }

    @Test
    fun `Allow expired fully vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val start = case.vaccinationDate

        fixtureAllowExpired(case, start)
    }

    @Test
    fun `Allow expired booster vaccinated`() {
        val case = SampleDocuments.ErikaMustermann.EudccBoosteredVaccinated()
        val start = case.vaccinationDate

        fixtureAllowExpired(case, start)
    }

    @Test
    fun `Allow expired recovery`() {
        val case = SampleDocuments.ErikaMustermann.EudccRecovered()
        val start = case.startDate

        fixtureAllowExpired(case, start)
    }

    private fun fixtureDeclineExpired(case: SampleDocuments, start: DateTime) {
        fixedTimeRule.setCurrentDateTime(start.plusYears(100))
        val document = documentManager.parseEncodedDocument(case.qrCodeContent)
            .blockingGet()
            .document

        documentManager.addDocument(document)
            .test()
            .assertError(DocumentExpiredException::class.java)
            .assertError { it.message == "The document has expired" }

        assertDocumentStored(document, false)
    }

    private fun fixtureAllowExpired(case: SampleDocuments, start: DateTime) {
        fixedTimeRule.setCurrentDateTime(start.plusYears(100))

        val document = documentManager.parseEncodedDocument(case.qrCodeContent)
            .blockingGet()
            .document

        documentManager.addDocument(document)
            .test()
            .assertComplete()

        assertDocumentStored(document, true)
    }

    private fun assertDocumentStored(document: Document, expected: Boolean) {
        documentManager.getOrRestoreDocuments()
            .toList()
            .test()
            .assertValue { it.contains(document) == expected }
    }
}
