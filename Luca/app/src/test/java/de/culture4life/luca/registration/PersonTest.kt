package de.culture4life.luca.registration

import org.junit.Assert.*
import org.junit.Test

class PersonTest {

    @Test
    fun getSimplifiedFullName_nameWithEdgeCases_expectedOutput() {
        assertEquals("TOM SSSMEIER", Person("Prof. Dr. Tom Jerry", "Süßmeier").getSimplifiedFullName())
    }

    @Test
    fun equals_sameNames_returnsTrue() {
        assertEquals(Person("a", "b"), Person("a", "b"))
    }

    @Test
    fun equals_differentNames_returnsFalse() {
        assertNotEquals(Person("a", "b"), Person("a", "c"))
        assertNotEquals(Person("a", "b"), Person("c", "b"))
        assertNotEquals(Person("a", "b"), Person("c", "d"))
    }

    @Test
    fun equalsSimplified_similarNames_returnsTrue() {
        assertTrue(Person("Dr. Tom", "Süßmeier").equalsSimplified(Person("Tom Jerry", "Süßmeier")))
    }

    @Test
    fun removeAcademicTitles_nameWithoutTitle_returnsOriginal() {
        assertEquals("Tom", Person.removeAcademicTitles("Tom"))
    }

    @Test
    fun removeAcademicTitles_nameWithTitle_returnsOnlyFirstName() {
        assertEquals("Tom", Person.removeAcademicTitles("Dr. Tom"))
    }

    @Test
    fun removeMultipleNames_onlySingleName_returnsOriginal() {
        assertEquals("Tom", Person.removeMultipleNames("Tom"))
    }

    @Test
    fun removeMultipleNames_multipleNames_returnsOnlyFirstName() {
        assertEquals("Tom", Person.removeMultipleNames("Tom Jerry Hardy"))
    }

    @Test
    fun simplify_nameWithNonAsciiChars_expectedOutput() {
        assertEquals("TOMSSSMEIER", Person.simplify("Tom Süßmeier"))
    }
}
