package de.culture4life.luca.registration

import com.google.gson.annotations.Expose
import java.util.*

open class Person(
    @Expose open val firstName: String,
    @Expose open val lastName: String,
) {

    fun getFullName() = "$firstName $lastName".trim()

    fun getSimplifiedFirstName() = firstName
        .removeAcademicTitles()
        .removeMultipleNames()
        .simplify()
        .trim()

    fun getSimplifiedLastName() = lastName
        .simplify()
        .trim()

    fun getSimplifiedFullName() = "${getSimplifiedFirstName()} ${getSimplifiedLastName()}".trim()

    fun equalsSimplified(other: Person?): Boolean {
        if (other == null) {
            return false
        }
        return getSimplifiedFullName() == other.getSimplifiedFullName()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Person || other.javaClass != this.javaClass) {
            return false
        }
        return getFullName() == other.getFullName()
    }

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        return result
    }

    override fun toString() = getFullName()

    private fun String.simplify() = simplify(this)

    private fun String.removeAcademicTitles() = removeAcademicTitles(this)

    private fun String.removeMultipleNames() = removeMultipleNames(this)

    companion object {

        fun removeAcademicTitles(name: String): String {
            return name.replace("(?i)Prof\\. ".toRegex(), "")
                .replace("(?i)Dr\\. ".toRegex(), "")
        }

        fun removeMultipleNames(name: String): String {
            return name.substringBefore(" ")
        }

        fun simplify(name: String): String {
            return name.uppercase(Locale.getDefault())
                .replace("[^\\x41-\\x5A]".toRegex(), "")
        }
    }
}
