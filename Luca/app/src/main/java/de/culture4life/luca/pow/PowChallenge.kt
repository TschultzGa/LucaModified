package de.culture4life.luca.pow

import java.math.BigInteger

data class PowChallenge(
    val id: String,
    val t: Int,
    val n: BigInteger,
    val expirationTimestamp: Long
) {

    val w: BigInteger by lazy {
        calculateW()
    }

    fun calculateW(): BigInteger {
        val two = BigInteger.valueOf(2)
        return two.modPow((two.pow(t)), n)
    }

}