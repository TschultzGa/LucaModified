package de.culture4life.luca.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Base58Test {

    @Test
    fun decode() {
        assertArrayEquals("Hello World!".toByteArray(), Base58.decode("2NEpo7TZRRrLZSi2U"))
        assertArrayEquals(
            "The quick brown fox jumps over the lazy dog.".toByteArray(),
            Base58.decode("USm3fpXnKG5EUBx2ndxBDMPVciP5hGey2Jh4NDv6gmeo1LkMeiKrLJUUBk6Z")
        )
        val result = Base58.decode("111233QC4")
        val expected = asByteArray(0x0000287fb4cd) // leading zeros will be removed by Kotlin
        assertArrayEquals(expected, result.copyOfRange(3, result.size))
    }

    private fun asByteArray(input: Int): ByteArray {
        var n = input
        val out = mutableListOf<Byte>()
        while (n > 0) {
            out.add(n.mod(256).toByte())
            n = n.div(256)
        }
        return out.reversed().toByteArray()
    }
}
