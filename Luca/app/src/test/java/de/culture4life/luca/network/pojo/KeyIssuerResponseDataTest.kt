package de.culture4life.luca.network.pojo

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.KeyIssuerData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class KeyIssuerResponseDataTest : LucaUnitTest() {

    private val keyIssuerResponseData = KeyIssuerResponseData(
        id = "d229e28b-f881-4945-b0d8-09a413b04e00",
        encodedCertificate = "-----BEGIN CERTIFICATE-----\nMIIF8jCCA9qgAwIBAgIUNraRTy+ykuT/pXzk+DfiBqHaPsEwDQYJKoZIhvcNAQEN\nBQAwbTELMAkGA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UEBxMGQmVy\nbGluMREwDwYDVQQKEwhsdWNhIERldjEpMCcGA1UEAxMgbHVjYSBEZXYgQ2x1c3Rl\nciBJbnRlcm1lZGlhdGUgQ0EwHhcNMjEwNzA5MTgxODAwWhcNMjIwNzA5MTgxODAw\nWjCBgTELMAkGA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UEBxMGQmVy\nbGluMREwDwYDVQQKEwhsdWNhIERldjEmMCQGA1UEAxMdRGV2IENsdXN0ZXIgSGVh\nbHRoIERlcGFydG1lbnQxFTATBgNVBAUTDENTTTAyNjA3MDkzOTCCAiIwDQYJKoZI\nhvcNAQEBBQADggIPADCCAgoCggIBAKow1660WFqNEgMpFaRqXOLgw8bIx4h8Zttk\nhWafkOCbNLW93Dlu7L+yvPzmWTXJ97pjIA4zABljJ17yh/K+7R2QjMWIFirHXbli\nOyn+maymTMrYAgb73QUCfzSBoTW9wGglmJMvpYW/uFNB+yFM/BemdR5CKtoKFtjY\nScIBbTfqrtZp8x815X6J0Ts5Iy0ltQKRQLrmq3CvDVCZnhzyC6LYyfAPTrSYunac\nYOWpyg0q9OXYqCskEGnuQN7ypMAbw9ku6hhdNmfKci+pO47Yy2IUcSa7ViAe9psU\nmEK8slkAtaKo0PoAZhCM4Rso2Ml6ah4xyyvloyFgzpyuZjWLyQK5So0Dv4uBUhXn\nY7ha5a2Ypxv7Qnv0AV8mUVfSRDM7FGRiO09v/S+8SJ+iszFQz3VxT6Nhp3cBhgz4\nplSokoLW+03efIiJOm5mQUx/5h1CQdAynbMJFiHa2DLRyOj2RDN9m8Rwo5nOWsVU\nF7M9N7zPwtHyRnTxa9FLb2xUytzEykibarTzcI7QqjJdALuxIvKeHnWT70LC8TCX\nMfIFh7Z6ZojXQTvfrJKeCtpRv8rBKmU4/GSIzDOH7vq5CLHnppj2ZXuypECYoWX7\nqoyvy8lxk0bYAGk/hndo9FzPLKWvnCmxovg3sMCtfG7Pt/006mZFFzhDoDULzKMd\nzOtChvEhAgMBAAGjdTBzMA4GA1UdDwEB/wQEAwIEsDATBgNVHSUEDDAKBggrBgEF\nBQcDAjAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQ2RdKX3FHzxyFgUsKV/8jnsB2o\n9jAfBgNVHSMEGDAWgBRMdbAGNCq//hXC9wYRlmcqAit07zANBgkqhkiG9w0BAQ0F\nAAOCAgEASlhUUeuZQAXabDqihPYeIAu5Ok3VhVtI2uEz1vlq20p7Ri3KQHUDFPu3\nwSELUr5rjmUhwDdB8Xsx9D+T+WzGKznIbx3m805Mp3ExDZ7qqyRbmWTE/6mi6R5A\nrGOzVtxkpbk0uukASRUf/PIDFasKo2XKkqJkNW905fSAncrRvQQBeIJQvK0HF2Pj\nv3n7Zxl2y+vT8oqSsoTfB+9IWJMtecHMjqe8qj3GB/uPyNYcuHi0/o3QW2wQB1Xn\nEeffrAjGk669gGUKuB2zcAcfBsQcfPQcRZEe7L+ExFHUklUujOeiMRFqm4qTlDyc\nabg1OiOaX48twR4CtXwuM40pQBOkj9e0NbhWmEWzP96rMtSRNlU/K4B2lbbJ3zWp\natBdAmv97xQd/3XC1SafxbtWXZo2s4AX7SzoQ4yIiae2RP1nC8/GxEApM6KXA5SD\nyxvtINpKU7cLAzP4cDMXc8/vDD7JOIzEwxRASo4pdQIaZBT+jRQ6BRRLxpYJyx2i\ng3vSCwENPv/Rpj4kobc46GsD/azmJ2ezMPVEEpJ63xFhEEHSNysGbq5JfrLHrQdB\n+bpxOFtliMb+QiLfiW4Lr+giq4OenJUb2TIPLjVnoJUjQLqQkrKIYccr0mXWzpUq\ntMk6sJ4QPw5+WeR/tceU56ekQzN/5ROeTTMtzAU8LENp+mpI42A=\n-----END CERTIFICATE-----\n",
        encryptionKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJBV0pyanZzbytJMW1yT0hybGFHanhGRFRZK2JveWRHMmw3RGRmS3hxYkJBenhRSzJRVjlzZEFCc0F0aDNFVWUya2lUUXhWMDlhWnpsd0xaY25oa1NhTT0iLCJ0eXBlIjoicHVibGljSERFS1AiLCJpYXQiOjE2MzcyMjg5NTJ9.kbJG4y9CEcyoexj78DHUBpNYHocNOYVJyS3nMxOMDXvDbSqIJbCwvIjQqI8y6zFTM4CEtBkdez5_6U-zZdQUJfUA_pX-Oz3CbQrjT3s7ERsz27xBsvu3uLAg4DH7Pjegxjnti3pFqQ1VHUe-5PGWQSlhaGvHzD47MrTp6nPvV3CKtJWa1DaC6tDNBKAI3fuP9NGA9pGmvJaJSNZPRIEhkheRCgVchd4GQIy-QyKd2hKGg6Eser_vSwEsN78Ogh8yTX4VVYnzsamOtw8PHkI9zRwEZzSxHkO59idKd3Lz-AVkEtjlotRTSKzyVBYwtwNo3wa6mAyBvwXuKHyota5U3Oyw6cn4CtCdQZNkF766-Vd30h39Ij-OgmTxLeQjQo5Xkc0H_BYl-_k8sWrPMTlqvUtVFvlyOEKj032xSlB-PGZOP922_vkIKGpHIgN_y-2gvbmu1OQISzdpPwt6BJHjE8538RthhnY9WDNKbyiVvzDOXQLnH7JcL-IGCLOFZI1zYNK2pTGzPUQPoE2Uk9A63Ma1AxnSFWyn2WtHhbKD4peWaUFmfGACR0NBKq9yd16GD-5dhXmwWB5eREwmCqt6iz3DODOAVG1pQcoWwzrBxhb-Bp92-7XwjvKmr_wYo6sNtjp-Slkw2tUrV000h3vCy_4fHGU9Qn2BNWNQQP_ys10",
        signingKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJNV21NUkxTaWRYTHJVd0ZRcjlWd1lCKzN6ckFtblV4T0xUWDl5OWxOR21HTzMxU3JCWEFJOXdOZklhNzVicTlWNVRJa1VVd21xOE1ONG9HRDBveUduST0iLCJ0eXBlIjoicHVibGljSERTS1AiLCJpYXQiOjE2MzcyMjg5NTF9.mSxbgr8nBBTMO9TBiw6gaT9eIDQrm8vOgp_UN40CN0YLWmhDPFfVHM18Vbo_Gewhei4ynKGdiXlNRDvtjCDIJFjYPRCUmQMxheM7Dmne1tATa0418F9toA6-muM_kCnFabZ1yknSRfErzyiFk1hFrcePZ7v5sghbIlobLIPgxksExH1N36Iz9KViFCJr8joWW3OQgLqIAA4nBPHTb8zIjtYixuWZMube2hFpSDAYkITKXHxdyGWusi4S8GDgXgEBNJiUPIwAE3Bj-HV4I8L1dgBu-DNCX30kp5VkmIfuC9BvW0VjjpDNmlOUUGUEHDErfZd-uuzoB1W6DkMP_AW90efkSYuKACmte_F8YFNb8m0GS2VKAaQBRWk53m0MZRRqyGEWzW2A5ckgfsScYD6ibc2wkhBhh_o-Nff95OOaZ9r1SqB4swnPWPrEULC_1gHFIUzfewkCz_yp3BLhsm5N7A3SA8xE0Sw672hMo1xbb58A9O-hJR5koxcVpvCPp_tXCUQh_-rdM1mr3BarAqSVNtovI8f7uibSnK6R6afB3t-zRsmxtuqVhF3aDTULqqofD-DAS-SGX46egZ89WMKqpoozpBCq8av6kzrqmurJ0sq_baQ4hpMuAQpquR0ablNC2i-oqpC3wfWohZurGvcQYev58tlDjfLgPSEJzP21XRw"
    )

    private val encryptionKeyData = KeyIssuerData(
        id = "d229e28b-f881-4945-b0d8-09a413b04e00",
        name = "Gesundheitsamt Dev",
        type = "publicHDEKP",
        creationTimestamp = 1637228952000,
        encodedPublicKey = "BAWJrjvso+I1mrOHrlaGjxFDTY+boydG2l7DdfKxqbBAzxQK2QV9sdABsAth3EUe2kiTQxV09aZzlwLZcnhkSaM=",
        issuerId = "6766ea7e43226228d5a8ecb095b6e43f265a826d"
    )

    private val signingKeyData = KeyIssuerData(
        id = "d229e28b-f881-4945-b0d8-09a413b04e00",
        name = "Gesundheitsamt Dev",
        type = "publicHDSKP",
        creationTimestamp = 1637228951000,
        encodedPublicKey = "BMWmMRLSidXLrUwFQr9VwYB+3zrAmnUxOLTX9y9lNGmGO31SrBXAI9wNfIa75bq9V5TIkUUwmq8MN4oGD0oyGnI=",
        issuerId = "6766ea7e43226228d5a8ecb095b6e43f265a826d"
    )

    @Test
    fun parseCertificate_validResponse_parsesCertificate() {
        val parsedData = keyIssuerResponseData.certificate
        assertEquals("CN=luca Dev Cluster Intermediate CA, O=luca Dev, L=Berlin, ST=Berlin, C=DE", parsedData.issuerDN.name)
    }

    @Test
    fun parseEncryptionKeyJwt_validResponse_parsesKey() {
        val parsedData = keyIssuerResponseData.encryptionKeyData
        assertEquals(encryptionKeyData, parsedData)
        assertEquals(keyIssuerResponseData.encryptionKeyJwt, parsedData.signedJwt)
    }

    @Test
    fun parseSigningKeyJwt_validResponse_parsesKey() {
        val parsedData = keyIssuerResponseData.signingKeyData
        assertEquals(signingKeyData, parsedData)
        assertEquals(keyIssuerResponseData.signingKeyJwt, parsedData.signedJwt)
    }
}
