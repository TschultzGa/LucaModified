package de.culture4life.luca.network.pojo

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.DailyPublicKeyData
import de.culture4life.luca.testtools.rules.FixedTimeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.security.spec.ECPoint

class DailyPublicKeysResponseDataTest : LucaUnitTest() {

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    private val dailyPublicKeyResponseDataWithoutExpiration = DailyPublicKeyResponseData(
        dailyKeyJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
    )

    private val dailyKeyDataWithoutExpiration = DailyPublicKeyData(
        id = 22,
        creationTimestamp = 1637923803000,
        encodedPublicKey = "BN68Uo0wieH8cGOscpsWkorhD+RIAMZpGcJ+NnohfWFw+iESY5obuidzOVVih52980No1Sn3/INZfLobdNcCEb8=",
        issuerId = "d229e28b-f881-4945-b0d8-09a413b04e00"
    )

    private val dailyPublicKeyResponseDataWithExpiration = DailyPublicKeyResponseData(
        dailyKeyJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJjMWY4NmMwYS1kY2E0LTQ0N2ItYjE2YS0xYzY0OTdmMTU5MGMiLCJrZXlJZCI6MSwia2V5IjoiQlBkQS9KZVhlWlNpS1dXMDFwUUkrSEFxR1JtV2N2ZU1zRm5SZWJ0cFFJSFVPZk1qVkoxa1dyZlRzZEJiQVQ2b0dsMG5jK0FlNlRYMkZ2Zk05N3A3eDI0PSIsImlhdCI6MTY0NzMzOTE3OSwiZXhwIjoxNjQ5NzU4Mzc5fQ.tTbaOu7hWhxLZiCkQbsDBeOS4PqoDbvXuwugL1G7Zle8qpFf44Zr1I9dOSQYnlOi_VZ8lj7lUacoAs9F25HknA"
    )

    private val dailyKeyDataWithExpiration = DailyPublicKeyData(
        id = 1,
        creationTimestamp = 1647339179000,
        expirationTimestamp = 1649758379000,
        encodedPublicKey = "BPdA/JeXeZSiKWW01pQI+HAqGRmWcveMsFnRebtpQIHUOfMjVJ1kWrfTsdBbAT6oGl0nc+Ae6TX2FvfM97p7x24=",
        issuerId = "c1f86c0a-dca4-447b-b16a-1c6497f1590c"
    )

    @Test
    fun parseDailyKeyJwt_validResponseWithoutExpiration_parsesKey() {
        val parsedData = dailyPublicKeyResponseDataWithoutExpiration.dailyPublicKeyData
        assertEquals(dailyKeyDataWithoutExpiration, parsedData)
        assertEquals(dailyPublicKeyResponseDataWithoutExpiration.dailyKeyJwt, parsedData.signedJwt)
    }

    @Test
    fun parseDailyKeyJwt_validResponseWithExpiration_parsesKey() {
        val parsedData = dailyPublicKeyResponseDataWithExpiration.dailyPublicKeyData
        assertEquals(dailyKeyDataWithExpiration, parsedData)
        assertEquals(dailyPublicKeyResponseDataWithExpiration.dailyKeyJwt, parsedData.signedJwt)
    }

    @Test
    fun expirationTimestampOrDefault_withExpiration_usesExpiration() {
        assertEquals(dailyKeyDataWithExpiration.expirationTimestamp, dailyKeyDataWithExpiration.expirationTimestampOrDefault)
    }

    @Test
    fun expirationTimestampOrDefault_withoutExpiration_usesDefault() {
        assertEquals(
            dailyKeyDataWithoutExpiration.creationTimestamp + DailyPublicKeyData.EXPIRATION_DURATION_DEFAULT,
            dailyKeyDataWithoutExpiration.expirationTimestampOrDefault
        )
    }

    @Test
    fun getPublicKey_validEncodedPublicKey_decodesKey() {
        val publicKey = dailyKeyDataWithoutExpiration.publicKey
        val expectedW = ECPoint(
            BigInteger("debc528d3089e1fc7063ac729b16928ae10fe44800c66919c27e367a217d6170", 16),
            BigInteger("fa2112639a1bba2773395562879dbdf34368d529f7fc83597cba1b74d70211bf", 16)
        )
        assertEquals(expectedW, publicKey.w)
    }
}
