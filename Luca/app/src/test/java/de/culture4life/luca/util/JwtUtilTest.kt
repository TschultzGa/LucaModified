package de.culture4life.luca.util

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.AsymmetricCipherProvider
import io.jsonwebtoken.security.SignatureException
import org.junit.Assert.assertEquals
import org.junit.Test

class JwtUtilTest : LucaUnitTest() {

    private val signedJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
    private val unsignedJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9."

    @Test
    fun parseJwt_validJwt_parsesJwt() {
        val jwt = JwtUtil.parseJwt(signedJwt)
        assertEquals("ES256", jwt.header["alg"])
        assertEquals("d229e28b-f881-4945-b0d8-09a413b04e00", jwt.body["iss"])
    }

    @Test
    fun getUnsignedJwt_signedJwt_removesSignature() {
        assertEquals(unsignedJwt, JwtUtil.getUnsignedJwt(signedJwt))
    }

    @Test
    fun getUnsignedJwt_unsignedJwt_returnsSameJwt() {
        assertEquals(unsignedJwt, JwtUtil.getUnsignedJwt(unsignedJwt))
    }

    @Test
    fun verifyJwt_validJwt_completes() {
        val encodedPublicKey = "BMWmMRLSidXLrUwFQr9VwYB+3zrAmnUxOLTX9y9lNGmGO31SrBXAI9wNfIa75bq9V5TIkUUwmq8MN4oGD0oyGnI="
        val publicKey = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()
        JwtUtil.verifyJwt(signedJwt, publicKey)
    }

    @Test(expected = SignatureException::class)
    fun verifyJwt_invalidSignature_throws() {
        val encodedPublicKey = "BMWmMRLSidXLrUwFQr9VwYB+3zrAmnUxOLTX9y9lNGmGO31SrBXAI9wNfIa75bq9V5TIkUUwmq8MN4oGD0oyGnI="
        val publicKey = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()
        JwtUtil.verifyJwt(signedJwt.replace(".Brz", ".I8f"), publicKey)
    }

    @Test(expected = SignatureException::class)
    fun verifyJwt_wrongKey_throws() {
        val encodedPublicKey = "BAWJrjvso+I1mrOHrlaGjxFDTY+boydG2l7DdfKxqbBAzxQK2QV9sdABsAth3EUe2kiTQxV09aZzlwLZcnhkSaM="
        val publicKey = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey.decodeFromBase64()).blockingGet()
        JwtUtil.verifyJwt(signedJwt, publicKey)
    }
}
