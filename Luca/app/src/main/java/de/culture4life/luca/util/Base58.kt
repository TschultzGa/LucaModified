package de.culture4life.luca.util

/**
 * Utility class to decode Base58 strings into ascii-based byte arrays.
 *
 * @see [Specification](https://tools.ietf.org/id/draft-msporny-base58-01.html#rfc.section.2)
 */
object Base58 {
    private val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val indices = alphabet.toIntList()

    @JvmStatic
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }

        // String to mapped byte sequence
        val base58Bytes = ByteArray(input.length)
        for (i in input.indices) {
            val char = input[i]
            val value: Int = indices.indexOf(char.code)
            if (value < 0) {
                throw RuntimeException("Given string is not Base58 conform: $input")
            }
            base58Bytes[i] = value.toByte()
        }

        // Count leading zeros to add later on as we deal with integers throughout the decoding
        var leadingZeroCount = 0
        while (leadingZeroCount < base58Bytes.size && base58Bytes[leadingZeroCount] == 0.toByte()) {
            ++leadingZeroCount
        }

        return transformToAscii(base58Bytes, leadingZeroCount)
    }

    @JvmStatic
    private fun baseShift(number: ByteArray, start: Int, oldBase: Int = 58, newBase: Int = 256): Byte {
        var remainder = 0
        for (i in start until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * oldBase + digit
            number[i] = temp.div(newBase).toByte()
            remainder = temp.mod(newBase)
        }
        return remainder.toByte()
    }

    @JvmStatic
    private fun transformToAscii(base58Bytes: ByteArray, leadingZeroCount: Int): ByteArray {
        val decoded = ByteArray(base58Bytes.size)
        var startPoint = 0

        var i = leadingZeroCount
        var index = 0
        while (i < base58Bytes.size) {
            val result = baseShift(base58Bytes, i)
            decoded[index++] = result
            if (result != 0.toByte()) {
                // Find last non-zero byte
                startPoint = index
            }

            if (base58Bytes[i] == 0.toByte()) {
                i++
            }
        }
        decoded.reverse()

        // Correct length in respect to leading zeros from before
        startPoint = decoded.size - (startPoint + leadingZeroCount)

        return decoded.copyOfRange(startPoint, decoded.size)
    }

    private fun CharArray.toIntList(): List<Int> {
        return this.map { it.code }
    }
}
