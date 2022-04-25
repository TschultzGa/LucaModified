package de.culture4life.luca.testtools.samples

import de.culture4life.luca.network.pojo.PowChallengeResponseData
import de.culture4life.luca.util.TimeUtil
import java.util.concurrent.TimeUnit

class PowChallengeSamples {
    companion object {
        fun powChallengeResponse() = PowChallengeResponseData(
            id = "aec4b300-d83d-4b35-bec9-7506a4d590f9",
            t = "722000",
            n = "144380763259650206165998492155351388825945452939423498473280949282313289136159375661015977582124727973311826782287838157897769664221001485570865841509663731386087436449391953482279620327058756416748340645252983772240122199269269171151377875385220439442022824366535997821105787938297760058083260498515463996677",
            expirationTimestamp = (TimeUtil.getCurrentMillis() + TimeUnit.MINUTES.toMillis(1)) / 1000
        )
    }
}
