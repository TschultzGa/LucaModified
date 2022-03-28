package de.culture4life.luca.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.Jwt
import io.jsonwebtoken.Jwts
import java.security.Key

object JwtUtil {

    @JvmStatic
    fun parseJwt(signedJwt: String): Jwt<Header<*>, Claims> {
        return Jwts.parserBuilder()
            .build()
            .parseClaimsJwt(getUnsignedJwt(signedJwt))
    }

    @JvmStatic
    fun getUnsignedJwt(signedJwt: String): String {
        val splitToken = signedJwt.split(".").toTypedArray()
        return splitToken[0] + "." + splitToken[1] + "."
    }

    @JvmStatic
    fun verifyJwt(signedJwt: String, signingKey: Key) {
        Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(signedJwt)
    }
}

fun String.parseJwt(): Jwt<Header<*>, Claims> {
    return JwtUtil.parseJwt(this)
}

fun String.verifyJwt(signingKey: Key) {
    return JwtUtil.verifyJwt(this, signingKey)
}
