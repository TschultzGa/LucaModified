package de.culture4life.luca.network.pojo

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.DailyPublicKeyData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.security.spec.ECPoint

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class DailyPublicKeyResponseDataTest : LucaUnitTest() {

    private val dailyPublicKeyResponseData = DailyPublicKeyResponseData(
        dailyKeyJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
    )

    private val dailyKeyData = DailyPublicKeyData(
        id = 22,
        creationTimestamp = 1637923803000,
        encodedPublicKey = "BN68Uo0wieH8cGOscpsWkorhD+RIAMZpGcJ+NnohfWFw+iESY5obuidzOVVih52980No1Sn3/INZfLobdNcCEb8=",
        issuerId = "d229e28b-f881-4945-b0d8-09a413b04e00"
    )

    @Test
    fun parseDailyKeyJwt_validResponse_parsesKey() {
        val parsedData = dailyPublicKeyResponseData.dailyPublicKeyData
        assertEquals(dailyKeyData, parsedData)
        assertEquals(dailyPublicKeyResponseData.dailyKeyJwt, parsedData.signedJwt)
    }

    @Test
    fun getPublicKey_validEncodedPublicKey_decodesKey() {
        val publicKey = dailyKeyData.publicKey
        val expectedW = ECPoint(
            BigInteger("debc528d3089e1fc7063ac729b16928ae10fe44800c66919c27e367a217d6170", 16),
            BigInteger("fa2112639a1bba2773395562879dbdf34368d529f7fc83597cba1b74d70211bf", 16)
        )
        assertEquals(expectedW, publicKey.w)
    }

}