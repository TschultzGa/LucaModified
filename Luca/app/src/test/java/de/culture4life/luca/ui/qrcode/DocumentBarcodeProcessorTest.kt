package de.culture4life.luca.ui.qrcode

import de.culture4life.luca.document.Document
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

class DocumentBarcodeProcessorTest {
    private val documentPerson1: Document = TestDocument("Max", "Musterman", 0)
    private val documentPerson2: Document = TestDocument("Erika", "Musterfrau", 0)

    private val processor = DocumentBarcodeProcessor(mock(), mock())

    @Test
    fun hasNonMatchingBirthDate_noMatches_returnsFalse() {
        Assert.assertFalse(processor.hasNonMatchingBirthDate(documentPerson1, documentPerson2))
    }

    @Test
    fun hasNonMatchingBirthDate_matchesSameBirthday_returnsFalse() {
        Assert.assertFalse(processor.hasNonMatchingBirthDate(documentPerson1, documentPerson1))
    }

    @Test
    fun hasNonMatchingBirthDate_matchesDifferentTimestampSameDay_returnsFalse() {
        val documentWithDifferentDOB: Document = TestDocument(documentPerson1)
        documentWithDifferentDOB.dateOfBirth = TimeUnit.HOURS.toMillis(12)
        Assert.assertFalse(processor.hasNonMatchingBirthDate(documentPerson1, documentWithDifferentDOB))
    }

    @Test
    fun hasNonMatchingBirthDate_matchesDifferentBirthday_returnsTrue() {
        val documentWithDifferentDOB: Document = TestDocument(documentPerson1)
        documentWithDifferentDOB.dateOfBirth = TimeUnit.DAYS.toMillis(1)
        Assert.assertTrue(processor.hasNonMatchingBirthDate(documentPerson1, documentWithDifferentDOB))
    }

    @Test
    fun hasNonMatchingBirthDate_withEmptyDocuments_returnsFalse() {
        documentPerson1.type = Document.TYPE_VACCINATION
        val document: Document = TestDocument(documentPerson1)
        document.dateOfBirth = TimeUnit.DAYS.toMillis(1)
        Assert.assertFalse(processor.hasNonMatchingBirthDate(documentPerson1, emptyList()))
    }

    @Test
    fun hasNonMatchingBirthDate_withSameDocument_returnsTrue() {
        documentPerson1.type = Document.TYPE_VACCINATION
        val document: Document = TestDocument(documentPerson1.firstName, documentPerson1.lastName, TimeUnit.DAYS.toMillis(1))
        document.type = Document.TYPE_VACCINATION
        val storedDocuments = listOf(document)
        Assert.assertTrue(processor.hasNonMatchingBirthDate(documentPerson1, storedDocuments))
    }

    internal class TestDocument : Document {
        constructor(firstName: String?, lastName: String?, dateOfBirth: Long) {
            setFirstName(firstName)
            setLastName(lastName)
            setDateOfBirth(dateOfBirth)
        }

        constructor(document: Document) {
            firstName = document.firstName
            lastName = document.lastName
            dateOfBirth = document.dateOfBirth
        }
    }
}
