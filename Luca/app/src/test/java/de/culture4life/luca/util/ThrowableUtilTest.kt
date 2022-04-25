package de.culture4life.luca.util

import de.culture4life.luca.LucaUnitTest
import org.junit.Assert.*
import org.junit.Test

class ThrowableUtilTest : LucaUnitTest() {

    @Test
    fun isCause_directMatch_returnsTrue() {
        assertTrue(
            ThrowableUtil.isCause(
                IllegalStateException::class.java,
                IllegalStateException(IllegalArgumentException())
            )
        )
    }

    @Test
    fun isCause_directMatchWithoutCause_returnsTrue() {
        assertTrue(
            ThrowableUtil.isCause(
                IllegalStateException::class.java,
                IllegalStateException()
            )
        )
    }

    @Test
    fun isCause_wrappedMatch_returnsTrue() {
        assertTrue(
            ThrowableUtil.isCause(
                IllegalArgumentException::class.java,
                IllegalStateException(IllegalArgumentException())
            )
        )
    }

    @Test
    fun isCause_noMatch_returnsFalse() {
        assertFalse(
            ThrowableUtil.isCause(
                NullPointerException::class.java,
                IllegalStateException(IllegalArgumentException())
            )
        )
    }

    @Test
    fun getCauseIfAvailable_directMatch_returnsMatch() {
        val expected = IllegalStateException("Expected", IllegalArgumentException())
        assertEquals(
            expected,
            ThrowableUtil.getCauseIfAvailable(
                IllegalStateException::class.java,
                expected
            )
        )
    }

    @Test
    fun getCauseIfAvailable_directMatchWithoutCause_returnsMatch() {
        val expected = IllegalStateException("Expected")
        assertEquals(
            expected,
            ThrowableUtil.getCauseIfAvailable(
                IllegalStateException::class.java,
                expected
            )
        )
    }

    @Test
    fun getCauseIfAvailable_wrappedMatch_returnsMatch() {
        val expected = IllegalArgumentException("Expected")
        assertEquals(
            expected,
            ThrowableUtil.getCauseIfAvailable(
                IllegalArgumentException::class.java,
                IllegalStateException(expected)
            )
        )
    }

    @Test
    fun getCauseIfAvailable_noMatch_returnsNull() {
        assertNull(
            ThrowableUtil.getCauseIfAvailable(
                NullPointerException::class.java,
                IllegalStateException(IllegalArgumentException())
            )
        )
    }
}
