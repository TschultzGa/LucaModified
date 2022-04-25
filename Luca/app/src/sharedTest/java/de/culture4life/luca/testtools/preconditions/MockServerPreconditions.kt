package de.culture4life.luca.testtools.preconditions

import de.culture4life.luca.BuildConfig
import de.culture4life.luca.network.pojo.attestation.AttestationNonceResponseData
import de.culture4life.luca.network.pojo.attestation.AttestationRegistrationResponseData
import de.culture4life.luca.network.pojo.attestation.AttestationTokenResponseData
import de.culture4life.luca.network.pojo.id.IdentCreationResponseData
import de.culture4life.luca.network.pojo.id.IdentStatusResponseData
import de.culture4life.luca.testtools.rules.MockWebServerRule
import de.culture4life.luca.testtools.samples.PowChallengeSamples
import de.culture4life.luca.testtools.samples.SampleAttestations
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.serializeToJson
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import java.net.HttpURLConnection.HTTP_OK
import java.util.concurrent.TimeUnit

class MockServerPreconditions(private val mockWebServerRule: MockWebServerRule) {

    enum class Route(val method: String, val path: String) {
        // TODO: Re-use existing interfaces

        // LucaV3
        SupportedVersion("GET", "/api/v3/versions/apps/android"),
        TimeSync("GET", "/api/v3/timesync"),
        RedeemDocument("POST", "/api/v3/tests/redeem"),

        // LucaV4
        PowChallenge("POST", "/api/v4/pow/request"),

        // AttestationV1
        AttestationNonce("GET", "/attestation/api/v1/nonce/request"),
        AttestationRegister("POST", "/attestation/api/v1/devices/android/register"),
        AttestationAssert("POST", "/attestation/api/v1/devices/android/assert"),

        // LucaIdV1
        LucaIdCreateEnrollment("POST", "/id/api/v1/ident"),
        LucaIdEnrollmentStatus("GET", "/id/api/v1/ident"),
        LucaIdDelete("DELETE", "/id/api/v1/ident"),
        LucaIdDeleteIdent("DELETE", "/id/api/v1/ident/data"),
    }

    fun assert(route: Route, skipTimeSync: Boolean = true) {
        var nextRequest = requireNextRequest()
        while (skipTimeSync && nextRequest.path == Route.TimeSync.path) {
            nextRequest = requireNextRequest()
        }
        assertEquals(route.path, nextRequest.path)
        assertEquals(route.method, nextRequest.method)
    }

    fun givenHttpError(route: Route, statusCode: Int) {
        define(route) { setResponseCode(statusCode) }
    }

    fun givenSupportedVersion(versionCode: Int = BuildConfig.VERSION_CODE) {
        define(Route.SupportedVersion) {
            setResponseCode(HTTP_OK)
            setBody("{\"minimumVersion\":$versionCode}")
        }
    }

    fun givenTimeSync() {
        define(Route.TimeSync) {
            setResponseCode(HTTP_OK)
            setBody("{\"unix\":${TimeUtil.getCurrentUnixTimestamp().blockingGet()}}")
        }
    }

    fun givenLucaIdCreateEnrollment() {
        define(Route.LucaIdCreateEnrollment) {
            setResponseCode(HTTP_OK)
            setBody(IdentCreationResponseData(0L))
        }
    }

    fun givenLucaIdDelete() {
        define(Route.LucaIdDelete) {
            setResponseCode(HTTP_OK)
        }
    }

    fun givenLucaIdDeleteIdent() {
        define(Route.LucaIdDeleteIdent) {
            setResponseCode(HTTP_OK)
        }
    }

    fun givenLucaIdEnrollmentStatusQueued() {
        define(Route.LucaIdEnrollmentStatus) {
            setResponseCode(HTTP_OK)
            setBody(IdentStatusResponseData(IdentStatusResponseData.State.QUEUED, REVOCATION_CODE))
        }
    }

    fun givenLucaIdEnrollmentStatusPending() {
        define(Route.LucaIdEnrollmentStatus) {
            setResponseCode(HTTP_OK)
            setBody(
                IdentStatusResponseData(
                    state = IdentStatusResponseData.State.PENDING,
                    revocationCode = REVOCATION_CODE,
                    identId = ENROLLMENT_TOKEN,
                    receiptJWS = RECEIPT_JWS
                )
            )
        }
    }

    fun givenLucaIdEnrollmentStatusFailed() {
        define(Route.LucaIdEnrollmentStatus) {
            setResponseCode(HTTP_OK)
            setBody(
                IdentStatusResponseData(
                    state = IdentStatusResponseData.State.FAILED,
                    revocationCode = REVOCATION_CODE,
                    identId = ENROLLMENT_TOKEN,
                    receiptJWS = RECEIPT_JWS
                )
            )
        }
    }

    fun givenLucaIdEnrollmentStatusSuccess() {
        define(Route.LucaIdEnrollmentStatus) {
            setResponseCode(HTTP_OK)
            setBody(
                IdentStatusResponseData(
                    IdentStatusResponseData.State.SUCCESS,
                    REVOCATION_CODE,
                    ENROLLMENT_TOKEN,
                    IdentStatusResponseData.Data(
                        "eyJlcGsiOnsia3R5IjoiRUMiLCJjcnYiOiJQLTI1NiIsIngiOiJuZlZLU1I1b0VvRnB1Vk9XT1p4ajc3Ry1yWUtsQjk4M09ONzRENlJTWEhBIiwieSI6ImxBUjBjY0VuLXBKMk1KanREbl9rYVptYmRuVVcyMnQ3VGJzbDNiNFY0RUUifSwiYXB2IjoiQXAwUFdVN21fWGNvamgxVVo2ckhiSjZTak5vWmx6VmhzMUwzY0pfSnVyVSIsImVuYyI6IkEyNTZDQkMtSFM1MTIiLCJhbGciOiJFQ0RILUVTK0EyNTZLVyJ9.YimOxdD4fXf6OCNEq0Z05n6yFpBFzxs7UqejvFA05M6JHVlPJVq9ZKnqqtyELHGuyx4dqBdkv7hM8IGh-pK_7MeqLgpeGgtK.ReXARaYgxkykMy5jAn2M9w.V-p-k9-X2p1ewd_7AA-sQCtdOXiy8u9_AHb9iNqd8cN_QMkgWFfU80aZTsLzv8jK39fl6S3X1KENgvlEknBPiiQmfDrb797VTVq6xE71ObbxNwsZbLxgBiWQuMlDW2gHe_0EIOntR_VmVFX67qVTH6PIp_qJnrTTiuMOSWRK-YbpuJuyd9lV3VdCk63_CefDwPyPs7W4DIGsdPG5gUff-Gpdy6XqRrjKY7Wlq9HvJ-30eVRhiVthIdHL9ATTk0Ne-fXYUC0Nzqrk-Eks6RtkmXTII8dgTrhXG64mhIxEbV58C98anoJXlC3cHl220zWDcGxtQ-fQd5ewxKexWObI1DzvCJC46P3j5WYgocES6h_vX8m_hMQ44TcHJuK98x131LafYucF3PHLZlnJxeuRWhw58BqysN8xmfh95nGLV_CDJT2TXVRt37u6ObUQ4c2f2q58T2Xh93nGJwHbfTOMGu4o189omUvskBygwAM9aTIeLOTeEwiVWKGs3mT49MnwiV6sCeYwDxZr2OJAko2Q73_AFPX-1DR-yGnBPwnJRjfK_iRwfFMYG4ZGvOJRHSw1qSH5duzO3t7WvMufyKGtC4w5TqQkp9QLMn0WK8TqLpfm3EQfinCfoJrmd974PCLlo-8SNu73n2_fFM-fTbyHRl42qZcUpa-q1dz6bNQgaW5pEsFS0vnNXHo5lo7KefC12folXeo1GwPcbjICOtpk2flexXqbkGM_tJk4wqLuXNxXRHdh8GgVUd65CmWp8rgmH6LBdLYOcisc9PWgptKcY6Kz3UdujBmOinzSfHw6yGdtnSq05r55qk6xj6ZJXSra39yU3CvPOTN9xEm-42PEMHDPRRA1-VeMfkE5K_kwsPDDz2sGave2gbUIZsWn1IcrKA7oman_mW4lReoCkNMmcX2DHdTAAm8TncRyikKeM03cn0urkY8dY-dq33Rr0Aw9zVuSnNlhQKxYB9o2e4qX-vfH4VLZDigOdcgLy2_A2wsRzUQGBWioLhpfherPMy5q-8dJgfx2RxY2ufh1PMSMRm4AI5IRq42otbnOEy36ANf4mpD-unSx-7V8u9zPuw0uVdtUOud1zVg5AgzdSSrifYaov0-uTAEZ0eGnJSNp1mTNwAZKYWYj10GXCoqem9HMyf6imsqnbXkB8Ps_jcvcs4PJwic0Yi3yAT3I8mOnG22JNegcsl9UcXtGwPIaBmt-8tc-oWbJabmmBtj8mFXGSB2jq0N5WPYO602NpN9BbvRf6gKXD9ejP_Hus05UxP57o_sPfHg977r1KTdOruImwFU7OpWUkGKXiqba4ouNb224E9sg3Cca3OgqsKmYJ_7R6_tNjlK5kV6DJpu7g-diLqfaSGqMUwjrUu-shDwAHIbEKYWbATbdWuK2e3fOS8GTMqpTEL4ng9wHE_BiA2K-T6PU0ehiXgXaQR7-4LZoSasOlKzYzQ3ygW-wjD8706THU3CVN49X1F1Z1geinTOHD6O6rwyVzEITvpJdb05mVueSWm38dt0gnsumyL_f5FCg52YmzjV4pmDp-Io4FDiMYyp-5UeUpVPbraXdvrzqJz4XIkRBU5Va3-f-oGsqM3LayNvG-nh3YIkj9fv-8SGt_lnSfqZGSHk9VgJ54Rcc-gftVKqvIw1o2DZzgPQTKbpNiATYXpRM5WVpWTL4J5JQGd-jWY-FRKPWcCacvMgtZh7n6elh2J9ww7-kz3U6bi-9tyjtsOzZagPpdNENIiF9rI0zVtrwsyAebSd5Y9xcYF5md0_3E2xCefr5ORKrtoDTbgYH18k6iIhUvXlHg6ttfxiQ-NEtq1GrruYTaC2hd_Del7UCkGHKu0JVwGc-TXZH7zFgS-h3L-WlXxENtnVj6y0qg_iZ5_N4QBCiMUvjQMU4KyUnHUFooshzd8yle83dmQEp-rrqdCfsBckfXLNHS4Id34s5Kut4qC2kBrJogIC58m6W853aGYk53pUDI4iPgIIiA1fTslnd3jgdfIZMBYyT3ctCLjz5NLNUyNLRScx_JQldi4S4l8GCWLpan1fSoIxauqiI_7M7wxkS428KHN8lOphewBg2Rq3ainFBcFpbXhdvNGaQjX-nffC7WceKN2mMGnPsxJY50Z9bxLMezKy6_FRKhD8DsLb8AxcyzYBT4VEKF9DZHdK6E7zb5u7KeoZMMN0eJi9DELwra38AyRuq3cSaxCL6EPRk4vLFHzDf0WEAo79jS1jRACElt2qojAcfNOzCafQyRR_TICvsiqWhcAUEYSCs426oKiezHewn3QsQ-BMQQWhu4K222bxYg9HBzVoi3A9YNd6VAMxSJ2fyRu2ExTagx9MQFCCYyb1K4BaQ3At4Jnf_RWPx3GBp-00v4GqBYbDMCaftsnqx9DfCbrNedQSC_D599vn7bBJS0H7_Ps4fY8UxmJvNyMxmvrEK_fKAAYAteKE8d-Dq4ZGkNDu4m0yOice3oBHTskt4dYyW7uwoGtDvPUY1i9fDACBY70p9naRVjkinNfuoG8TdbOgEuNtY7SQ2cDbq2eueaaciBOmLII-0KHHAqBlMC-pDk012pXOm0s0MS3mEZk9lbTWqw30ZmCVclKMUOMERQZmF5GAb7UmLSDOLWAp0K6EV9X3H2yFXVxkuYOR_4PipqpG9xNwuC_k5t0guAew.fzolthm6y8mMFq7dD6jWNDH0_OeR5zJBGWE9L_JAsIY",
                        "eyJlcGsiOnsia3R5IjoiRUMiLCJjcnYiOiJQLTI1NiIsIngiOiJHRkNPaVhNcW5RajVKckd0OGVVZDBvUktmRG0tdlJXUmJleDNFRWd0Qi04IiwieSI6ImtCOFo3WlFDcHo0UEVKUXJLR190ZVJkMG1zMlhpRkpBaFlmX0VPbzFDYmMifSwiYXB2IjoiQXAwUFdVN21fWGNvamgxVVo2ckhiSjZTak5vWmx6VmhzMUwzY0pfSnVyVSIsImVuYyI6IkEyNTZDQkMtSFM1MTIiLCJhbGciOiJFQ0RILUVTK0EyNTZLVyJ9.Ywmi4g3Z2P1__5KNdJ18ugKu9AcQRTynXSH6YDyICPSEFD31j6JvTpyezBoaArUm2IELGE9biCol9Rejc691IuvUSTKdkngT.jNFIcq46Li-If8oOF4Bbmw.G6X6XI1HBedaVpqFZBYuskM0VZGUte_Va4SXE0kLuoAl5X_2g8-GBTRTU27QEDVorGtcfJb1Wi8lWBmiKVtnvBgA8oQs1Nxu8o1Aj2lmOkvApLJJPX2s2cvpPYyVkWLZN7ZivbNyYYbDH4jipt45KXgYy_hVQshI9cpgoqtKt-uA2Z5__a5Jk9nl3LDTIqy9eCKAyzEUpEiUUjBb3uP30Dyr7fE0kM5NvvrL_uW_ME3zocrzGzKlw8MlPmRHNoLyGzt50V-4h12BZXYLHG8gu9zXFS4gyNH78PvmX0ukynPRBVkPXvsHNyfvs5lnA10OziVzM-w7lJfc-_QcylWqFjzAQz7t1OscWU1nIIj6x-RGYnIQ6T39bqWwxkYiLPEPMRiHi4Ciqdnmgeg_6gl1gXLghdla2ZdQAA0ssUizq3wM9T90nY81njqWpP6iRB_Fxwz0fdkynvC4nNJUmellU65K4QXGk0S3HDJktlb3qtKlONxqdI-cMD4-3ZfaOcUxHzELwT9LvSbu6RnxAaqvXJZUbDh1IauhnlwdSZ1C2XIpSrpwMeCUdG1K8OG9zHzVfLySyDe5_2QmCPAu2t30_NVKajuvbAusR4cPjPIKJwxYAAyIuZAGxqdhDelEgzD6UM6LTkvwSuPDlhQ1BQT_vVrVSVfgEIAZcSBavAbJXnroqBrkHOvHgYitkyp00vEN56BXUfkBLtM5P4o1MBZWGm71-qrOoNYDw6gpfjxrXMyGy7O8L5-tHD-md1WRdRl7_qqU0YlbCakuD1pcVeKP3gBqISHLihl3aVTMRqHGvdJrKroMXUNgN7NJeu6qwiR_VIlt1q-8M2cdV9mGtCaZqlBRQ7Lgs0M8UUeX6OzJ6XT6hRdkrU7jBZeA8M9l3ycwY7R8h8bf0bUqW9f7rxDa4CkVjsK0YOy5D2LDpbdaF5khRiRE86HGMVqHIKUA9ptQHjsgRO3thLHvTW-DFSNjnlFvsSieYuO1dcL6TnsXY4dGKnvi7tNhN9SLxq8GWgithLypmkYJU5sfnDp-unudc0-T009bK_fA4EItd6RDN5rpy1ez8jBt-8n16VVHTSCFcXTdXnnEw1y7HnWCstjFOc9-c3le1tvGE7kBUATNNUViwQKHZcuf3ZbaGTRjKxIeSvCC1ChOPAL4PNFA7uPcAmJBLblNRa9nhXWxVEHKKVnSJlq2EqSK65JAn3pJIYNRi63EFGAq--lM9fIVeXxtWRtcuNezu8TQrtofasDpASgOSyWs-kvxFCGLE-mshFpq_xUeMzYy3M1QHWYPk9l0-cRkCZiSkhWvxiEogDSAYRrGITSMliCHGBxt-tm1qMYPWHfWKYQpuFmjBivK4Z01tMQWu4DvNtTcw84anNBIxurauJzdjRONeDSXYMYaWHY36ATTPdsvV5yyQMyv4JRGoK5594Iu5MvNFkv1ooB6h6ATNQBsqjbzCnjw1fSYcJ-Z8w8rRQSUyjAR5_OFKQYE4_oUCBhuHUViTVGWqV1-FYnBj2P0jwU2Ae4-vd3wuJll-7QnjyUhBPVwtOfo3ZYZ2J4sercT0n6QTFA_AoZT-ET78L9jfDyQzqzuJQx42qnMsQXBnDQcakH_YcmWp7n7jfO1RpWw6ScGyXB8U4_3ir3IldsIvSg36XoEZ62GwB7_Mb_d7K53xRimVosBx3SCEw19PeXSufnU3IeabMZkNLa89d3nSRH4AHWmbQEd8fbEpSqD9vmboMQubdhiqqBTWy6s365y6zkFnBCqmhk8dc0NVubQuC_Rt7wAB9sgh-c0_-yPXqB3cjWUe77Zoe-6_sE9PIo23O2dWwKIx7G3gSf_BzwTf3K4i0waLOh4g5lZKdwaIZrIjqWnKwDqwXZyBWiShvTVF-x4qUF_2iZWisZQOFQHtlCwfpZRKbBsvwOuL3iV3uvkEqD74nYqZmInXwqatcdgh-G5aZVZhNUoQfggKwp8oQXaDyCmrQ3W5rAxx176l-P0ukSwqXeYaWtES6pWJ9kFAeyHRexfCfKxRXqtvibJwDFDASK-0wwbD7hJVCJSHmtcdaUlvf_UNMYXpsg51IiBt29CixwSCFc647SsXfFjH0-vZl2FwpviCLhUt4UeW4mGfYxsHo51pruFW1pTVRRvgE-j79NurpvWQqPlTnTgk_eg-05nZtjxkUq4PqeCIbZIfIAz0nYOmfRVXqolbNCbxM6hnvb8at0Ef9Xl3ypQhWtXoP3XunOUsm6JyBxp9swis8jfhp0mccToIjk8W-uTbPPpvDO_fIn93Yp8au-A4R7Jp1Nc4_jiIJubUn8CrfX4ATjdAj9OFfJYzdFYsq2JQlFKmqlI8umTB-B-nqR8Offvmetd4KMgRYCKsrSD7Rvw0jercN140lPD-EYCRjcJHVXPi_oRWnuMklk2j6Mi1_YlM3AKqAskWHfNdUMvVI8aUyyawI8i6mq_OCqOzUPPe_EaKEIKrerVfwA0iZnrJ_QJem8tpSI0EseDjiY8tD2uYpQBzW0yTb2R-BPUd2tjLAg3cUjkr_zsKj7V_6-4OLShuZO_RcNtVYM86JF9qri4tiJLEqRLbKEZ0iIk9OM6x-5uckO9CQjV6119zRqfYLE32YW8kft10my8iVlwiLFRbe2fNp7scEfnUEt74o15pGBtw2ZsEHAZr1jyqqhgXNZj05lYl1tXaaODu3GtG3aadKf4JfUiLVuRIingfZtzfbVzBYAA7yaa6R1rbU6U4U1NKdoW3P7NlLM4frUr6d3I7FIwcc8Dx_bz_SsQX_pBK6dkJJTzOP3iS_qJRpSQZSqNlwCodW0W0zrnhPKNcubl7OAbk-lfLa_ZBLjgrusEtaF-WFcHc5J7g1g.R4rYoYEbuizXH0PUEKBu9M8s4swtLktpBDuZkzAEnHA",
                        "eyJlcGsiOnsia3R5IjoiRUMiLCJjcnYiOiJQLTI1NiIsIngiOiJaWWRibmdUaGFGc0loc2VHVnJtcG8zdTRMUXg4SHN6elRiWGxoZTdfelBvIiwieSI6InRIem5xekE5LXM2U21ER01FZ0k1cF9ETEFBdWpXWEhaUU9yYndRZmNHM2cifSwiYXB2IjoiQXAwUFdVN21fWGNvamgxVVo2ckhiSjZTak5vWmx6VmhzMUwzY0pfSnVyVSIsImVuYyI6IkEyNTZDQkMtSFM1MTIiLCJhbGciOiJFQ0RILUVTK0EyNTZLVyJ9.y0jm0yyB7aGievIdCwoTRj7xe_t4megc0jZMXbi7VZf2NlTZzhOYoZ33TWk9WhmWoQxPwrdW1RFSZPCFELSNv2HAk45xYYRR.mIDQZcEAg2XRWTnvdT6Utw.9WJl73-EydgGZvy3729HyyCA3UnIFBUtormJPWAb6udduvBlbr-YJY65Iw5vsuBNqiPdH7Z9JHCEXec9fM5GtdDNmPxE95KV48YYwmKA7OgX5jkiOhLNcvx5CWZdtTy1W3WVT3Im2TFhYbX2OfUjz6WZCmelt0nC3Fs1ayvHpz6Lug7PbxYlzxGdkIXBZiSeik0u2rN_C1Cfm6tTzUpB_JeM576GflmIpVNO-KdbsQkXKJE6APGi1QE-bbjXFdvwKB5d6lj5zf7a-nHLni9m4u-DlNyfWU3B8OY0rvEIS-MXE6lRusTNoV_BjInGBDtK5KoMIHcqZdGUebyMcUR2W0cVhrFkgckaKhh4fb9Q1wjW-iDXN8DTj2dc1iqCNBLNNSeVcMWDWqhuhSB3zwTQ5ku3H-4SK4K-cPb-kOYleXQYC9vnLjjFilSVD3XN5igW2GoTbwv6HuAq4gqAwOxMscicWIw0mf0X4OU_fK0qoVRdT3Q419FJcMMdjpTqfRBN44zYsdVZyu6t2s_LI50VqHgPolrgLhtyQAYWoIlVs2oIpqUxwyX64_4Ac8IOhtioGtCmFASYM3uFVVfb5e-CC0dlBdt8eWmuzdJVASjiHqP5jK8a6U83GsbpuWSbVU4acbqtJ_FL58-9_77321R88vlNPni5ATHGAMcJLEC6BPN83JDesAm7Afghm6S_41Vb1zuiS4Wa0iEygzadXF8-6KUyvRiZmo7MdEHpTbaOYY0vQ5zjkVzqaGVjKBTeIOGTHEpdWOoCW-_CsaaGEARITbJsATZdFp-KTxy1IQhQVmhv0QC2oO-mDnahp0pBMC_7OlXGUsPv4pLObNjan7zvI-rgWCI5Gs3dlVFKR0OBns1uxID8knJXBMwFAbIVADC-EtjZAzsc1ZDZ9w3qdaV3P2m_Wz6Syg3yho4vOKV-pk-tZ65mPWZaazFRDP-WodeeRU9ilAnOajNYeBo31SMAplRuLxookYKe23rraiuwxBcUj-pptliKEPUFd3sJmyNOOSt9_5v7U_FZkMzNloG0_g5ohqaJETq4Zs3wYkkuz-rsQv9NjKib6DAX33q6oRUWw0bum96u3S2RWtRrAoYx6cKO6JHV3gisdl3GAIbQt8tVfqqaklDHWcYtPgOoG_mBfF5bRgWjXEDL_lQDprm7qzUfbwv_9QTpOqAkSKZUKeb1eIZ16xN3SRrVXM0cWEbeUHmQRxBnMHJLv9rjw8efW-UIcjk5nGMVn9FPy20bR8brQwongmfl8KGge0zCpawPjPGBbMi6hQJ5k0C_Os2mv9kQ83u100C5oEq4mi2zqQcjNFSu5bh1WYIbhapU_kvftIMP180oAwt5dxu2jEIgfG8rrkM26josh239QG6FXtgl9S187nfbIZ8kT4o_lfiELu4YFzTZwM_kIc4RTjVJZA2gGmsQtrgkmUC_Pk_fhckxDPe3Ar_EVWqTI1cBOtvmAQow0OqVlqs5aHdU7jWigkJS4j6AyLS8ykqDk735B2m9X63qDEf5VpuVi6diPjB18WvehkzcaXKvP8gmJws8WMmmeiXDeCW2eqgz1jNs2xvYhmqYVO8HAk9WqtTUYdfo41tkqjoTX01O2iOOu201QZfZEEe9ofCAZ_J3djKTLkHJhBps06VcSeQGaEYyVwO2g3EDeYwtZb51i7IkcRbQSf0sA8zKp4rNKX8gRJr-6EvR7m85EpzEXPkCmNuGBmarCsVWUeM7iGhdeU3Z0_Ix4GOpZBeJO55cQGnvVFD3K560p_5aDbVR4rd1o5CdJTil-kbE3s9vekqIdZ2OUjUNNJbqOHxf2748MskfYg5CYMwbVkhPrfuSeZzhlwHhHJ8_UwfAifINFp7_8el0anndu-pVFMd0w0qm7r8agfqjZYrCsBK8y86UWS0NN-QGlkIuwwufjNEfShlxcgassPJbAZhZQVoS7pLUIpq7au-9mGyiGW7Jl_8pSYS0vnbJ-b6hdOtCe_8KHvZnCdW8bhnuHcR727wt7rNmuNAfy9oh7cTDnnz89ZqkywamGc3-R7ZeG5Suo6EnRc2fEiGYaWN7cKMJh3P2X_W-6o6kBOVPREvKygozdq8eRZ9mJJu6XkJAyhtKpRKlo1W046wPVqs3LaWJLcnVFsWrG-CLFbrbQf2Jn8OzoVvRVyyc2qx_Kf9RATmCjoMyj806OUZ8gbCXx2ZBXcGmcZc8Oi5OObFuiu184j6pOAv5G416hsqZIEoBPsxaeEZlfQdE74NV4UHa1C4TGFC1T5n0hjgFmXYge5ZokTX6EZBz64WequvdCzcfnN1dsTi06MHGztflKPjVC-d91pRfQ97FWBdmw4JrAdQh9BnDJZbNsQApS88ZgaZZjexjjtqHjUFc4ek5ovQjl4cWy5AoXk9wNntKv3ZnB5CSQQS9EvtPg-yu5cy5JQpCVxd-Sqaf5yGqhAwzNZJkaAoibO2p21og8bnGzzWJTmP3_nj4Uiwr26_z07fux8yJKEYgJnXSZhJi5yCSH4Rmgyim1BoGHZ8R6xtsz6R1MeYKZBsjnbo2bj_eWs03AbtKZO4RJbYDyeeNHGvAcaHMN8n2NqGq9Quku5GF0LbyO0kkXRZvEB5gfqH-Obahs6sel-Ho18Qa0SO_RypXQ3kBdn3Wg8ZcNIO8llgq8iS6E_gPPyvRgKfsSagSlYcLXNyXJQHgrnSiVkuog2T6h3AKJhLYTlhoV3e51ato88KDzRw.OGGoYgCrzijw2iDPpN1eSpo3oUoZ_03gMlmJf-pOqxM"
                    ),
                    RECEIPT_JWS
                ).serializeToJson()
            )
        }
    }

    fun givenPowChallenge() {
        define(Route.PowChallenge) {
            setResponseCode(HTTP_OK)
            setBody(PowChallengeSamples.powChallengeResponse().serializeToJson())
        }
    }

    fun givenAttestationNonce() {
        define(Route.AttestationNonce) {
            setResponseCode(HTTP_OK)
            setBody(AttestationNonceResponseData(SampleAttestations.Valid().nonce()))
        }
    }

    fun givenAttestationRegister() {
        define(Route.AttestationRegister) {
            setResponseCode(HTTP_OK)
            setBody(AttestationRegistrationResponseData(SampleAttestations.Valid().deviceId()))
        }
    }

    fun givenAttestationAssert() {
        define(Route.AttestationAssert) {
            setResponseCode(HTTP_OK)
            setBody(AttestationTokenResponseData(SampleAttestations.Valid().tokenAsJwt()))
        }
    }

    fun givenRedeemDocument() {
        define(Route.RedeemDocument) {
            setResponseCode(HTTP_OK)
        }
    }

    private fun define(route: Route, mock: MockResponse.() -> Unit) {
        mockWebServerRule.mockResponse.add(route.method, route.path, mock)
    }

    private fun MockResponse.setBody(arg: Any) {
        setBody(arg.serializeToJson())
    }

    private fun requireNextRequest() = mockWebServerRule.mockServer.takeRequest(10, TimeUnit.SECONDS)
        ?: throw IllegalStateException("No more requests recorded.")

    companion object {
        const val ENROLLMENT_TOKEN = "dummy enrollment token"
        const val REVOCATION_CODE = "dummy revocation code"
        const val RECEIPT_JWS =
            "eyJ4NWMiOlsiTUlJRUZEQ0NBZnlnQXdJQkFnSVVGNnZBWldCVHJTWWxYUVltZnJDbGN0TXRLQlV3RFFZSktvWklodmNOQVFFTkJRQXdSakVMTUFrR0ExVUVCaE1DUkVVeEV6QVJCZ05WQkFvVENrbEVibTkzSUVkdFlrZ3hJakFnQmdOVkJBTVRHVWxFYm05M0lHeDFZMkVnUVhCd0lGUkZVMVFnUTBFdE1ERXdIaGNOTWpJd016RXdNVE0wTkRBeldoY05Nekl3TXpBM01UTTBOREF6V2pCRE1Rc3dDUVlEVlFRR0V3SkVSVEVUTUJFR0ExVUVDaE1LU1VSdWIzY2dSMjFpU0RFZk1CMEdBMVVFQXhNV1NVUnViM2NnYkhWallTQkJjSEFnVkVWVFZDMHdNVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCSjgwVm5oRVV6cEpFbnRMZUxDc2VRc3RrMHZmTzBZRFwvSEFIM0ZKSHltajB5MVJDeG1xQjlFbDViQzNOeUZEZXFXdFBXTThyOU5BNzNsTDhcL2tcL1JubmlqZ2Njd2djUXdIUVlEVlIwT0JCWUVGSFBqK1dudDk4TnFBZXhNa1h1cnFhWjB6NWpBTUI4R0ExVWRJd1FZTUJhQUZIbHVPNytsK0doTWJOWkxwWkRhRVJqZ1oyeFpNQXdHQTFVZEV3RUJcL3dRQ01BQXdEZ1lEVlIwUEFRSFwvQkFRREFnV2dNR1FHQTFVZEh3UmRNRnN3V2FCWG9GV0dVMmgwZEhBNkx5OXpjMmt0ZG1NdFpXMXBkSFJsY2kxelpYSjJaWEl1ZEdWemRDNXBaRzV2ZHk1a1pTOXdkV0pzYVdNdlkzSnNMMmxrYm05M1gyeDFZMkZmWVhCd1gzUmxjM1JmWTJGZk1ERXVZM0pzTUEwR0NTcUdTSWIzRFFFQkRRVUFBNElDQVFDdG11RXA3V3FJcU5RNHJpdWliejZCMTdxVHZ1bGdMMURRYStKNFh6Z0FQQ1JRQlM5VE5oNE91SzJFTEhQdmNCbFdJa2s4QkRCWndtbzBxMUNpUmRQa01qSzNJelJWUjBrU1JGOUN6amlqNUs5a1lrNnAzQkVreTdWc3p6WVV6WVJcL0xXWkxBU2oxVG40RlwvUGJ3Z3ZvOHdvcXMwXC9zSzhzc2FpZzBaRXFYXC8xNjBmSFJxaUZ5ejQ5XC9JYmNDcGMwSlFpQUc4TFppc3RMMHEzRlN6VGlzT1E0UE9vaFpNd25RbVBFaHFTQzhtTjBLTXV0QXAxTGJFN2RhblJKRVM5akJ1YnJQM0RWVEJwbVwveERsUGt2MG9QZVltcmJmb1gram1rcXRHc2VpNGZ6UElTRk1lZGhSUXhIZ3BoZVwvbjg3XC9YTW1qTGUrRmwzZXdzZ1wvU2hraFlhUUQyc3JtWlwvTWs4T1FPeVdsSjVDczNkeWJkWFpVRE5abW9yMHZXRXRRSHpjc09jYjIxSHl3VUF6OTNkUHpsbWh2dEMzVmszVENhTjNTc0o0T3ViQU5hV2xIYm1MejBubXVLajlwMGtmdkNhZUQzYTYrMUhSN3FJcjdyXC8rTDJad3RMT2cxR0NsV3MycFlyNnZjREJcLzhvZHpjTWVzM056NndCdmMyT3N6dnBucHcyMFFnUjJoM0FlUnllSWVmZDNPTkNDOTYrTk9ZZkJHSEZ3bmJUSzhpRVZjRE9ORVlDZW52cUJ3MkhucEliTnBPZDhyOVBUdEl1K1A0YW90a1B6YXVzZVZYb052Q3dTVlJFVjlUUVhaV2hYN2hVNHg1RDFwVGZyVDFDZFM4WXJsXC9MRHYrKzNzU1EzaXl6bFRHQ0NYeEtzYXF6UmxLbkVtWmJxZFwvZExZK0wzZz09IiwiTUlJRlwvakNDQSthZ0F3SUJBZ0lVS24rRUJBam5qTWFIdzg1ZzlTbitXbXhOeVhNd0RRWUpLb1pJaHZjTkFRRU5CUUF3VVRFTE1Ba0dBMVVFQmhNQ1JFVXhFekFSQmdOVkJBb1RDa2xFYm05M0lFZHRZa2d4TFRBckJnTlZCQU1USkVsRWJtOTNJRlJ5ZFhOMElGTmxjblpwWTJWeklGSnZiM1FnVkVWVFZDQkRRUzB3TVRBZUZ3MHlNakF6TVRBeE16UXdNek5hRncwek1qQXpNRGN4TXpRd016TmFNRVl4Q3pBSkJnTlZCQVlUQWtSRk1STXdFUVlEVlFRS0V3cEpSRzV2ZHlCSGJXSklNU0l3SUFZRFZRUURFeGxKUkc1dmR5QnNkV05oSUVGd2NDQlVSVk5VSUVOQkxUQXhNSUlDSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQWc4QU1JSUNDZ0tDQWdFQTZQR1dBTkZheXV6bklYZ1VNblIyTjh3Q0RKWUhSMzRTTVNWRWJja0t1WXV3QlkyVFRlcFUyT2lucEdFb1djMnU4cmcybGdZQ05KdGtpV2ljREQ0eEdVaWJLMWpxemZpSjNVTlRxSjhtb2dtdGNTQ0lCeDhDWmpjNTRwSFJSdDRQcHJHcm41SnVMNjdZYjNRV2pPSmI5QUUyRGdnazFwS0FBRE1zVzByXC9BS2M3bThBSDFmd2N4Y0REQTMwb0dNNlVxZ2FWRzJES3lmWjR4YU1lWXdFeGdPY1Joc2d4cTZ1ZThvMDd5Mk9jOUZyZW9vK25UaGV0UXBkODRqYnNQMm9lMVwveXZDclgwd0o5ME5VUExmWHZ2ZFVZcW81M1VWNVVXQUw1aVNScVhaUmFQUWYyYms4K2Z3dEdPUEx4N3MzQzJiS2lBVjkrSkw1dnl1SEo5MDgrY3RGa0ZGam9iOWd3VlRzSlBSTnpDQkZxYmNTVFA4dlhseDhqbmNsXC95ZWtudGtxTExlZEk3c3lwUHZBNWxDOXZMa2VNdG5HYlpGQkF5Z05WSVE4VU1iUFM4SUFmZFhORGpVN3dYdTlYdTJ1WlozTkFYOEJkOUlVZ2JaNTF3ZDdjejB6SzBTT3F0cmdsb2pyUGJBTWNkYkpyK1JmNjUyemdpM1pjQzJsc1RSdzlrM1VhUUlXNkVRWnpkamYrQkx6TTF1MkNUbU1id0ZTNXM4YjN2bkJZaFVrZXB0R3R0cXN1SVV0eTJXdWRqa0pJYVwvV3UzMFlHMm9EOTdSXC9OQlBGeVpwUldJb3ZuUG14ajlleThaaDhzeG5BWVg0eWZIR2dzZWFZeVwvV1dtZVRrSGI4SjZaZzVSbXdteFdBSWNqRlp1SkRjZXo1Y2FSaHltVnF6Y0JXYlwvdVdJMENBd0VBQWFPQjJEQ0IxVEFkQmdOVkhRNEVGZ1FVZVc0N3Y2WDRhRXhzMWt1bGtOb1JHT0JuYkZrd0h3WURWUjBqQkJnd0ZvQVVnWnBxdHRVRTdiT0hqQVp6bUFZUUkzeU1nSUl3RWdZRFZSMFRBUUhcL0JBZ3dCZ0VCXC93SUJBREFPQmdOVkhROEJBZjhFQkFNQ0FRWXdid1lEVlIwZkJHZ3daakJrb0dLZ1lJWmVhSFIwY0RvdkwzTnphUzEyWXkxbGJXbDBkR1Z5TFhObGNuWmxjaTUwWlhOMExtbGtibTkzTG1SbEwzQjFZbXhwWXk5amNtd3ZhV1J1YjNkZmRISjFjM1JmYzJWeWRtbGpaWE5mY205dmRGOTBaWE4wWDJOaFh6QXhMbU55YkRBTkJna3Foa2lHOXcwQkFRMEZBQU9DQWdFQVBDd0pCa0FwcEllYjY5N1AxY0FoMVJURW1lcTlUQ2RNU0poMGpzQUN5T2tTU0dqVW5kTTZKY3ZGOGVFUUh6NFRJVnNsY1FVWUVCQjNFZWxvZGhCdXZsV01uclV2T1wvNkxYOUY4RHdHZCtWWFFWQysxakUrUTVEM0pKcWNhUjlyUlVwNDE5VExySWZqZ0cyWXZxMVRnMTVHY01zNXJHaEh0cm9mblBaVGswQUNSWE5uNXQ3dTExRUM4czFVd1hCb1ZWdENlT3RwXC95WDVOdUdOQTBYbXN4Wmo3UFVrZlpwOTVDK25pYkdwOHNjSWZtZlZnQXBvMWdCWlhrXC8zQ1wvNzh4NUYxMDBTUlZDQkYyb21WYkZUWXlcL1JLN0lETkgyU202Z1BoUjkrUW5lNGtnSzJEWlZtODZkU3RYSVwvZVViRGpxSVRBdTd0eFNwa3MwM1g2UFgyU0V3dWZ2cm1mY0RNcmVMM21wRnZYb1o3SzJCdmdtSTVlc09ack5lZ3pxcWh4V1NHUlRTMXpDUEkxRWl2SVNJNTZwY1FTdkNxZEVTbmlqbGFMeXo0RnBrRWZCcVwvT3R6XC9CaEx2YXVIXC9JcjJaWlwvaVBcL1BhKzd4ZW1WZ201R1luVjZ3OFwvNEJXaTJQdXdCTmZMcFwvY1RmUHduOXdoN0dcLzBsNlp0WmRoN2xBTmorSmxXYmdYOW8xamFiY2pHcCt4enB4UFA0WU9qY1JDWnQyeFBVb2xleHd4WE45Y2t2R1d3Y2Y4NkdSR1JaaXBKKzB0T3BVa2VKWjA1cjhcL0VreVM3RmYxOXJscm5SWjVVZDhOM1wvXC9LU21NTFRzaFBWMXc4QU5QXC95ODJpMGdyaVwvVVB0eFRkOTFQd3l4ZjlRcFFpNEJESjkxR203RVNxUXlYU1RBQ1ZqZ3VlRUU5UT0iLCJNSUlGa3pDQ0EzdWdBd0lCQWdJVUkxbmErU1gyYWREM052bldPNmM3eVF0YkZ5NHdEUVlKS29aSWh2Y05BUUVOQlFBd1VURUxNQWtHQTFVRUJoTUNSRVV4RXpBUkJnTlZCQW9UQ2tsRWJtOTNJRWR0WWtneExUQXJCZ05WQkFNVEpFbEVibTkzSUZSeWRYTjBJRk5sY25acFkyVnpJRkp2YjNRZ1ZFVlRWQ0JEUVMwd01UQWVGdzB5TWpBek1UQXhNekV4TXpOYUZ3MHpNakF6TURjeE16RXhNek5hTUZFeEN6QUpCZ05WQkFZVEFrUkZNUk13RVFZRFZRUUtFd3BKUkc1dmR5QkhiV0pJTVMwd0t3WURWUVFERXlSSlJHNXZkeUJVY25WemRDQlRaWEoyYVdObGN5QlNiMjkwSUZSRlUxUWdRMEV0TURFd2dnSWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUNEd0F3Z2dJS0FvSUNBUUMzZGM0VVFPSkVCZVZcL29KWEk0anBpOThhTWF0R2RcL3RZUkdmWjd5WTNHSDF6TVZZXC9RU2pLbTZydXAxY0hieUtnV0FtWVhtaHEyVStBVzltWE1yNGhUWlJwY0hIQTkwSWdGT3ZvXC9HN3J3UGFpdFRcL3M4MTJVbE9iSFRRQko4M0dvOVl3RzlIa1ZDOGdOaGY5VGkrZWM4ejRRWVNLOFwvbWRGUlY3UDhzM2wrWE9SemphaDhRY1g3VDFGdmd0ZWwzMnlPRmp5cWtvNkZjN0s4SWtrQXJQTHVXV2FnWEoxQlNBUUUwT3ExbmVROSswUWsyZHZlS1JUVGNwTEpid0FjTUhOT1VwdUFMQjMzRG9QRnZIZUNLdFJjM1hWbk1zMmhKY2I0cXhRZlM0dlFhdUtFSjZHSkI3MGZvZ2RDbEZpNlRIVnFUaVJES2ZzeEI0cFJia2s5cWp1TGxQZFBEc2h3OWRHNG1NdWRzeEtoWVRKQ0Q3ZFZ3RWJNK1RGQzRUYTY1UEloRTJUNkkyMkIrdVBLYVp2ZmNBVVBGd0xiSE9tV2xTdUhwckh3M21PTTd5RmdpT3dFOHV0UURCM3JQV2hjQ2pic2dIMERTRE5udnFFTEU1Z0F5UnBLY09KZXNHVFQxOXB0dFBaODRJcjlOb3JwdXZLblp5S0dSaVhwaUhKNm5qZ3dVNVRIVUNPMkZldzdCUGREd1BPNXRmVDdEWVJ5RGhvcmErdHdUTTNLQ3FEdjRvSzNkSFprcFdaTjZLUnVBM0h2QzFGNENvMzRKMUdGVUtTcnJCcHF2XC9SOHQrUjRpWExrdFpIYmZVQVB0QlBzMHprZ2d6d2twNFwvZXlcL2ZXRFpQZDA5SWpcL01selE0UEdoTnVxV2xKNzB3UnRYZmJvRXI3Y1wvWWhVTnJ3XC9HUUlEQVFBQm8yTXdZVEFkQmdOVkhRNEVGZ1FVZ1pwcXR0VUU3Yk9IakFaem1BWVFJM3lNZ0lJd0h3WURWUjBqQkJnd0ZvQVVnWnBxdHRVRTdiT0hqQVp6bUFZUUkzeU1nSUl3RHdZRFZSMFRBUUhcL0JBVXdBd0VCXC96QU9CZ05WSFE4QkFmOEVCQU1DQVFZd0RRWUpLb1pJaHZjTkFRRU5CUUFEZ2dJQkFINDhsTXRwY2p0emZETHA1cGU2Q2k4VnIwUW9sXC9YR0hhVCt5NWlXVmNYMVBCUzdjeVdseThEOW10ZGVpcUVvRmo0OWtjRnBMeGZlWUhjYWhLZTk2WWZwblJOK3NwaUp2cDRnNmhaZFhjRjM2aFk2UHpRWUp0aU02Tit3UnJ4R2d5S0VWa3FpbEI1VVdKZXRwazNxclkyK1dVbFZiSkZQKzNTZVBhQVN3RTZRQU42TkxGNENqd05kQlk4dDhPUEdpMlwveTBtMGhvbE82aE5mY0ZlRFBRWXFURStUOXJBUE5qWGxDazhTRkpTSWd3WGdsY0tQR1BzeWY4eWVvMEtrNlJnVERcL05aeFFlejJmYkNweXo3c09RY0dpVmRHTWN5WlJPUmlxeEo3VE5qRUUwY214S2tVcnd1bGZuT0ZxNUp1a25ldXduRERjdFFtTGZtR0VERmtnRFFiMlBhb3JxXC92VnFoYktxRjJhSlplVGs4c0lxQlBNUWdsaFl1S014cVJJWTdoQ0krV2hpc1lEODFtZjVnQkJpK05rM2pvenQybHpsRzRCM1FRZElGVmlDZExkTHRHVzJkNkk1XC8xaFwvRHk1d1lvb3V2bnRCUlZJQ2MwTGJ6UEpNcVlENkJvdGNDdm1DWmxGTElLTnJvS054OEhSQlVoWkl6ZVFzVUR3TG9XOGMwOWRNMWl5cllyRk9NbVdvUVp1bHZQTGNcL25SNHBxdzFpTW94U1wvT3kyXC9SdDNhR1dWTFJPYTgxQmR1ZzFQNVA3WkpBalhLUHpjYXhQMkczM1UwVTNKNm9ZY2J4ZmNpY3VyV01BMXBKSGdXWGVJaHI1d0hEWFA0XC9xM3VrYkR6RzM0dXRiaWtjdnBaVVZLZWlIdEpHU0pcL0VEUmphRW00Tjg4Q1pFRDRCTGRwIl0sImtpZCI6InNlcnZlcktleSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJpbnB1dF9kYXRhIjp7ImJpbmRpbmdLZXkiOiJNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVuZG5DT090ZWZPbk16RXRMalNVT2pmR04zTm5WNjBzMGVURGxELzVJTTRDZjZZN2ZUOHgyS3hsdmE5RUdnTm1QNFkyQzZJc201KzhudURRc3lCbDVnZz09IiwiZW5jcnlwdGlvbktleSI6Ik1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRWM1dVdCZFZuTGR2SGN6ak1sczJEcmw1cTFWVjBWMWxocDNtTUZHOVJMTyt2RG1DMXVMdGNtSDQxMk1MYk90Vyt5d2pEYUxVRURERDNoNzREQ1hWY3B3PT0iLCJjdXN0b20iOiJYLU1BTlVBTFRFU1QtSEFQUFlQQVRIIn0sImF1dG9JZGVudElkIjoiVFNULUxQRkpOIiwidHJhbnNhY3Rpb25OdW1iZXIiOiI1NjYzMWEyZC1jZjcyLTQ4YzMtYmY3OS05OTk5ODA1OTI0MjYifQ.SLehZZTbzPWHgHaHAMbMH7muv5XgJ0ENMdJkbCOdQrhCTy9YwyqPt-ITEdfkkqHoQPYmo4ZE6zNtHdLGl4lrvw"
    }
}
