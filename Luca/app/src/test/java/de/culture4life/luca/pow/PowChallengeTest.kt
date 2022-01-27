package de.culture4life.luca.pow

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.network.pojo.PowChallengeResponseData
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.util.concurrent.TimeUnit

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class PowChallengeTest {

    private val powChallengeResponse = PowChallengeResponseData(
        id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
        t = "722000",
        n = "144380763259650206165998492155351388825945452939423498473280949282313289136159375661015977582124727973311826782287838157897769664221001485570865841509663731386087436449391953482279620327058756416748340645252983772240122199269269171151377875385220439442022824366535997821105787938297760058083260498515463996677",
        expirationTimestamp = (System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)) / 1000
    )
    private val powChallenge = powChallengeResponse.powChallenge

    @Test
    fun calculateW_validChallenge_expectedW() {
        val expectedW = BigInteger(
            "52571132426657360441427045055983635933515940268316843025244762401389879291316542034234242948932966607766801473906759577822628298915837996423643809935627166826033619751573226601902604144301515523634718827548161237613335611612569397881443713947965167915036023670897926017830374588217369745916695296266512681931",
            10
        )
        assertEquals(expectedW, powChallenge.calculateW())
    }

}