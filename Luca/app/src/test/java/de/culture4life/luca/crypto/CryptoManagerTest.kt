package de.culture4life.luca.crypto

import com.nexenio.rxkeystore.provider.mac.RxMacException
import com.nexenio.rxkeystore.provider.signature.RxSignatureException
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.AsymmetricCipherProvider.decodePrivateKey
import de.culture4life.luca.crypto.AsymmetricCipherProvider.decodePublicKey
import de.culture4life.luca.genuinity.GenuinityManager
import de.culture4life.luca.genuinity.NoGenuineTimeException
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.endpoints.LucaEndpointsV4
import de.culture4life.luca.network.pojo.DailyPublicKeyResponseData
import de.culture4life.luca.network.pojo.KeyIssuerResponseData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.util.*
import de.culture4life.luca.util.SerializationUtil.fromBase64
import de.culture4life.luca.util.SerializationUtil.toBase64
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.concurrent.TimeUnit

class CryptoManagerTest : LucaUnitTest() {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var networkManager: NetworkManager
    private lateinit var genuinityManager: GenuinityManager

    @Before
    fun setup() {
        val preferencesManager = PreferencesManager()
        networkManager = Mockito.spy(NetworkManager())
        genuinityManager = Mockito.spy(GenuinityManager(preferencesManager, networkManager))
        cryptoManager = Mockito.spy(CryptoManager(preferencesManager, networkManager, genuinityManager))
        cryptoManager.initialize(application).blockingAwait()
        val getCurrentTime = Single.fromCallable { TimeUtil.getCurrentMillis() }
        Mockito.doReturn(getCurrentTime).`when`(genuinityManager).fetchServerTime()
    }

    @Test
    @Throws(InterruptedException::class)
    fun concatenateHashes_validData_expectedOutput() {
        cryptoManager.concatenateHashes(null, "Test".toByteArray(), ByteArray(0))
            .map(ByteArray::encodeToBase64)
            .test()
            .await()
            .assertValue("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFVTLqq9lXSIDb92ubjMAIMsIKbsET1oIplVDXpuDzReJeOwxEKY/BwUmvv0yJlvuSQnrkHkZJuTTKSVmRt4UrhV")
    }

    @Test
    @Ignore("Fails on Jenkins only")
    @Throws(InterruptedException::class)
    fun updateDailyKeyPairPublicKey_validKeyPair_verifySuccess() {
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA)
        cryptoManager.updateDailyPublicKey()
            .test()
            .await()
            .assertComplete()
    }

    @Test
    @Ignore("Fails on Jenkins only")
    @Throws(InterruptedException::class)
    fun updateDailyKeyPairPublicKey_noGenuineTime_verifyFails() {
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA)
        Mockito.doReturn(Single.just(false)).`when`(genuinityManager).isGenuineTime()
        cryptoManager.updateDailyPublicKey()
            .test()
            .await()
            .assertError(NoGenuineTimeException::class.java)
    }

    @Test
    fun assertKeyNotExpired_validKey_completes() {
        val key = DailyPublicKeyData(
            id = 0,
            creationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(8), // older than 7 days
            expirationTimestamp = TimeUtil.getCurrentMillis() + TimeUnit.DAYS.toMillis(1), // in the future
            encodedPublicKey = ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
            issuerId = ""
        )
        cryptoManager.assertKeyNotExpired(key)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    @Test
    fun assertKeyNotExpired_validKeyWithoutExpiry_completes() {
        val key = DailyPublicKeyData(
            id = 0,
            creationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(6), // not older than 7 days
            encodedPublicKey = ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
            issuerId = ""
        )
        cryptoManager.assertKeyNotExpired(key)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    @Test
    fun assertKeyNotExpired_expiredKey_emitsError() {
        val key = DailyPublicKeyData(
            id = 0,
            creationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(6), // not older than 7 days
            expirationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(1), // in the past
            encodedPublicKey = ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
            issuerId = ""
        )
        cryptoManager.assertKeyNotExpired(key)
            .test()
            .assertError(DailyKeyExpiredException::class.java)
    }

    @Test
    fun assertKeyNotExpired_expiredKeyWithoutExpiryTimestamp_emitsError() {
        val key = DAILY_KEY_NOT_EXPIRED

        cryptoManager.assertKeyNotExpired(key)
            .test()
            .assertError(DailyKeyExpiredException::class.java)
    }

    @Test
    fun hkdf_validInput_expectedOutput() {
        cryptoManager.hkdf(
            ikm = "77f84ac8bb5ea7cde72f983bce000987".decodeFromHex(),
            salt = null,
            label = "share_history_tan".toByteArray(),
            length = 32
        ).map(ByteArray::encodeToHex)
            .test()
            .assertValue("b35bca06abed087a38399b559c67fbde2b5f53aac2fa2d3f0840a6a9eeeb4b6a")
    }

    @Test
    fun ecies_validInput_decryptableOutput() {
        val ephemeralKeyPair = KeyPair(
            decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet(),
            decodePrivateKey(ENCODED_PRIVATE_KEY.decodeFromHex()).blockingGet()
        )
        val receiverKeyPair = KeyPair(
            AsymmetricCipherProviderTest.decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY),
            AsymmetricCipherProviderTest.decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)
        )

        val data = cryptoManager.hash("Hello World".toByteArray()).blockingGet()
        val eciesResult = cryptoManager.eciesEncrypt(
            data = data,
            ephemeralKeyPair = ephemeralKeyPair,
            receiverPublicKey = receiverKeyPair.public
        ).blockingGet()

        cryptoManager.eciesDecrypt(eciesResult, receiverKeyPair.private)
            .map { it.encodeToHex() }
            .test()
            .assertValue(data.encodeToHex())
    }

    @Test
    fun ecies_validInput_decryptsData() {
        val eciesResult = EciesResult(
            encryptedData = "de8e479ae44bf0091be601f0455adee9bd8ac5deef2b58ad3cd50c6460fe49e2".decodeFromHex(),
            iv = "cf7b86295cfef9259448a380348766e4".decodeFromHex(),
            mac = "d2405fd8431d28efe282880ecfff8a0cdbccab63d2a6d51a1366732be309d28d".decodeFromHex(),
            ephemeralPublicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet(),
        )

        val receiverPrivateKey = AsymmetricCipherProviderTest.decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)
        val data = cryptoManager.hash("Hello World".toByteArray()).blockingGet()

        cryptoManager.eciesDecrypt(eciesResult, receiverPrivateKey)
            .map { it.encodeToHex() }
            .test()
            .assertValue(data.encodeToHex())
    }

    @Test
    fun ecies_wrongPublicKey_emitsError() {
        val eciesResult = EciesResult(
            encryptedData = "de8e479ae44bf0091be601f0455adee9bd8ac5deef2b58ad3cd50c6460fe49e2".decodeFromHex(),
            iv = "cf7b86295cfef9259448a380348766e4".decodeFromHex(),
            mac = "d2405fd8431d28efe282880ecfff8a0cdbccab63d2a6d51a1366732be309d28d".decodeFromHex(),
            ephemeralPublicKey = AsymmetricCipherProviderTest.decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY),
        )

        val receiverPrivateKey = AsymmetricCipherProviderTest.decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)

        cryptoManager.eciesDecrypt(eciesResult, receiverPrivateKey)
            .test()
            .assertError(RxMacException::class.java)
    }

    @Test
    fun ecies_wrongMac_emitsError() {
        val eciesResult = EciesResult(
            encryptedData = "de8e479ae44bf0091be601f0455adee9bd8ac5deef2b58ad3cd50c6460fe49e2".decodeFromHex(),
            iv = "cf7b86295cfef9259448a380348766e4".decodeFromHex(),
            mac = "aa405fd8431d28efe282880ecfff8a0cdbccab63d2a6d51a1366732be309d2aa".decodeFromHex(),
            ephemeralPublicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet(),
        )

        val receiverPrivateKey = AsymmetricCipherProviderTest.decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)

        cryptoManager.eciesDecrypt(eciesResult, receiverPrivateKey)
            .test()
            .assertError(RxMacException::class.java)
    }

    @Test
    fun ecdh_validKeys_expectedSecret() {
        Mockito.doReturn(
            Single.just(
                DailyPublicKeyData(
                    id = 0,
                    creationTimestamp = TimeUtil.getCurrentMillis(),
                    encodedPublicKey = ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
                    issuerId = ""
                )
            )
        ).`when`(cryptoManager).getDailyPublicKey()
        val userMasterKeyPair = KeyPair(
            AsymmetricCipherProviderTest.decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY),
            AsymmetricCipherProviderTest.decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)
        )
        Mockito.doReturn(Single.just(userMasterKeyPair))
            .`when`(cryptoManager).getKeyPair(RegistrationManager.ALIAS_GUEST_KEY_PAIR)
        cryptoManager.ecdh(userMasterKeyPair.private)
            .map(ByteArray::encodeToBase64)
            .test()
            .assertValue(ENCODED_SHARED_DH_SECRET)
    }

    @Test
    fun ecdsa_validInput_verifiableOutput() {
        val publicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet()
        val privateKey = decodePrivateKey(ENCODED_PRIVATE_KEY.decodeFromHex()).blockingGet()
        val data = cryptoManager.hash("Hello World".toByteArray()).blockingGet()
        val signature = cryptoManager.ecdsa(data, privateKey).blockingGet()
        cryptoManager.verifyEcdsa(data, signature, publicKey)
            .test()
            .assertComplete()
    }

    @Test
    fun verifyEcdsa_validInput_completes() {
        val publicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet()
        val data = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e".decodeFromHex()
        val signature = (
            "3046022100fe8cfacd8921558782b4f0d7d534a932470b3d019397f19c42d1468817882335022100" +
                "e0e959cadbb1125c29c6baf8d93dd0fb678b60786c49436f780d7fb3f6026e25"
            ).decodeFromHex()
        cryptoManager.verifyEcdsa(data, signature, publicKey)
            .test()
            .assertComplete()
    }

    @Test
    fun verifyEcdsa_wrongPublicKey_emitsError() {
        val publicKey = AsymmetricCipherProviderTest.decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY)
        val data = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e".decodeFromHex()
        val signature = (
            "3046022100fe8cfacd8921558782b4f0d7d534a932470b3d019397f19c42d1468817882335022100" +
                "e0e959cadbb1125c29c6baf8d93dd0fb678b60786c49436f780d7fb3f6026e25"
            ).decodeFromHex()
        cryptoManager.verifyEcdsa(data, signature, publicKey)
            .test()
            .assertError(RxSignatureException::class.java)
    }

    @Test
    fun verifyEcdsa_wrongData_emitsError() {
        val publicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet()
        val data = cryptoManager.hash("Not Hello World".toByteArray()).blockingGet()
        val signature = (
            "3046022100fe8cfacd8921558782b4f0d7d534a932470b3d019397f19c42d1468817882335022100" +
                "e0e959cadbb1125c29c6baf8d93dd0fb678b60786c49436f780d7fb3f6026e25"
            ).decodeFromHex()
        cryptoManager.verifyEcdsa(data, signature, publicKey)
            .test()
            .assertError(RxSignatureException::class.java)
    }

    @Test
    fun verifyEcdsa_wrongSignature_emitsError() {
        val publicKey = decodePublicKey(ENCODED_PUBLIC_KEY.decodeFromHex()).blockingGet()
        val data = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e".decodeFromHex()
        val signature = (
            "30460221009854081e74c47bf21adc70e9729a152f626cfb3db4f9f0fbb30c38942187d590022100" +
                "daa35030d9d72452ee141b37751e9f3f379652360cb5e5fbba8a755c1d962087"
            ).decodeFromHex()
        cryptoManager.verifyEcdsa(data, signature, publicKey)
            .test()
            .assertError(RxSignatureException::class.java)
    }

    @Test
    fun encodeToString_decodeFromString_isSameAsInput() {
        val input = "abc123!§$%&/()=,.-*"
        toBase64(input.toByteArray(StandardCharsets.UTF_8))
            .map(String::decodeFromBase64)
            .map { String(it, StandardCharsets.UTF_8) }
            .test()
            .assertResult(input)
    }

    @Test
    fun encodeToString() {
        toBase64("AOU\nÄÖÜ".toByteArray(StandardCharsets.UTF_8))
            .test()
            .assertResult("QU9VCsOEw5bDnA==")
    }

    @Test
    fun decodeFromString() {
        fromBase64("QU9VCsOEw5bDnA==")
            .map { String(it, StandardCharsets.UTF_8) }
            .test()
            .assertResult("AOU\nÄÖÜ")
    }

    @Test
    fun verifyDailyPublicKeyData_wrongIssuerId_emitsError() {
        val dailyPublicKeyData = DAILY_PUBLIC_KEY_RESPONSE_DATA
            .dailyPublicKeyData
            .copy(issuerId = "ed557001-e324-4f38-b491-45a9261b3f87")
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA)
        doReturn(Completable.complete()).`when`(cryptoManager).assertKeyNotExpired(any())
        doReturn(Completable.complete()).`when`(cryptoManager).verifyKeyIssuerCertificate(any())

        cryptoManager.verifyDailyPublicKeyData(dailyPublicKeyData)
            .test()
            .await()
            .assertError(IllegalArgumentException::class.java)
    }

    @Test
    fun verifyDailyPublicKeyData_wrongSignature_emitsError() {
        val dailyPublicKeyData = DAILY_PUBLIC_KEY_RESPONSE_DATA.dailyPublicKeyData
            .apply {
                signedJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9" +
                    ".eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.ABCDQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
            }
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA)
        doReturn(Completable.complete()).`when`(cryptoManager).assertKeyNotExpired(any())
        doReturn(Completable.complete()).`when`(cryptoManager).verifyKeyIssuerCertificate(any())

        cryptoManager.verifyDailyPublicKeyData(dailyPublicKeyData)
            .test()
            .await()
            .assertError(io.jsonwebtoken.security.SignatureException::class.java)
    }

    @Test
    fun verifyDailyPublicKeyData_validData_completes() {
        val dailyPublicKeyData = DailyPublicKeyData(
            id = 22,
            creationTimestamp = 1637923803000,
            encodedPublicKey = "BN68Uo0wieH8cGOscpsWkorhD+RIAMZpGcJ+NnohfWFw+iESY5obuidzOVVih52980No1Sn3/INZfLobdNcCEb8=",
            issuerId = "d229e28b-f881-4945-b0d8-09a413b04e00"
        ).apply {
            signedJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
        }
        val keyIssuerResponseData = KeyIssuerResponseData(
            id = "d229e28b-f881-4945-b0d8-09a413b04e00",
            encodedCertificate = "-----BEGIN CERTIFICATE-----\n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\n-----END CERTIFICATE-----\n",
            encryptionKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJBV0pyanZzbytJMW1yT0hybGFHanhGRFRZK2JveWRHMmw3RGRmS3hxYkJBenhRSzJRVjlzZEFCc0F0aDNFVWUya2lUUXhWMDlhWnpsd0xaY25oa1NhTT0iLCJ0eXBlIjoicHVibGljSERFS1AiLCJpYXQiOjE2MzcyMjg5NTJ9.kbJG4y9CEcyoexj78DHUBpNYHocNOYVJyS3nMxOMDXvDbSqIJbCwvIjQqI8y6zFTM4CEtBkdez5_6U-zZdQUJfUA_pX-Oz3CbQrjT3s7ERsz27xBsvu3uLAg4DH7Pjegxjnti3pFqQ1VHUe-5PGWQSlhaGvHzD47MrTp6nPvV3CKtJWa1DaC6tDNBKAI3fuP9NGA9pGmvJaJSNZPRIEhkheRCgVchd4GQIy-QyKd2hKGg6Eser_vSwEsN78Ogh8yTX4VVYnzsamOtw8PHkI9zRwEZzSxHkO59idKd3Lz-AVkEtjlotRTSKzyVBYwtwNo3wa6mAyBvwXuKHyota5U3Oyw6cn4CtCdQZNkF766-Vd30h39Ij-OgmTxLeQjQo5Xkc0H_BYl-_k8sWrPMTlqvUtVFvlyOEKj032xSlB-PGZOP922_vkIKGpHIgN_y-2gvbmu1OQISzdpPwt6BJHjE8538RthhnY9WDNKbyiVvzDOXQLnH7JcL-IGCLOFZI1zYNK2pTGzPUQPoE2Uk9A63Ma1AxnSFWyn2WtHhbKD4peWaUFmfGACR0NBKq9yd16GD-5dhXmwWB5eREwmCqt6iz3DODOAVG1pQcoWwzrBxhb-Bp92-7XwjvKmr_wYo6sNtjp-Slkw2tUrV000h3vCy_4fHGU9Qn2BNWNQQP_ys10",
            signingKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJNV21NUkxTaWRYTHJVd0ZRcjlWd1lCKzN6ckFtblV4T0xUWDl5OWxOR21HTzMxU3JCWEFJOXdOZklhNzVicTlWNVRJa1VVd21xOE1ONG9HRDBveUduST0iLCJ0eXBlIjoicHVibGljSERTS1AiLCJpYXQiOjE2MzcyMjg5NTF9.mSxbgr8nBBTMO9TBiw6gaT9eIDQrm8vOgp_UN40CN0YLWmhDPFfVHM18Vbo_Gewhei4ynKGdiXlNRDvtjCDIJFjYPRCUmQMxheM7Dmne1tATa0418F9toA6-muM_kCnFabZ1yknSRfErzyiFk1hFrcePZ7v5sghbIlobLIPgxksExH1N36Iz9KViFCJr8joWW3OQgLqIAA4nBPHTb8zIjtYixuWZMube2hFpSDAYkITKXHxdyGWusi4S8GDgXgEBNJiUPIwAE3Bj-HV4I8L1dgBu-DNCX30kp5VkmIfuC9BvW0VjjpDNmlOUUGUEHDErfZd-uuzoB1W6DkMP_AW90efkSYuKACmte_F8YFNb8m0GS2VKAaQBRWk53m0MZRRqyGEWzW2A5ckgfsScYD6ibc2wkhBhh_o-Nff95OOaZ9r1SqB4swnPWPrEULC_1gHFIUzfewkCz_yp3BLhsm5N7A3SA8xE0Sw672hMo1xbb58A9O-hJR5koxcVpvCPp_tXCUQh_-rdM1mr3BarAqSVNtovI8f7uibSnK6R6afB3t-zRsmxtuqVhF3aDTULqqofD-DAS-SGX46egZ89WMKqpoozpBCq8av6kzrqmurJ0sq_baQ4hpMuAQpquR0ablNC2i-oqpC3wfWohZurGvcQYev58tlDjfLgPSEJzP21XRw"
        )
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, keyIssuerResponseData)
        doReturn(Completable.complete()).`when`(cryptoManager).assertKeyNotExpired(any())
        doReturn(Completable.complete()).`when`(cryptoManager).verifyKeyIssuerCertificate(any())

        cryptoManager.verifyDailyPublicKeyData(dailyPublicKeyData)
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun verifyDailyPublicKeyData_validDataWithExpiry_completes() {
        // TODO: add exp attribute
        val dailyPublicKeyData = DailyPublicKeyData(
            id = 22,
            creationTimestamp = 1637923803000,
            encodedPublicKey = "BN68Uo0wieH8cGOscpsWkorhD+RIAMZpGcJ+NnohfWFw+iESY5obuidzOVVih52980No1Sn3/INZfLobdNcCEb8=",
            issuerId = "d229e28b-f881-4945-b0d8-09a413b04e00"
        ).apply {
            signedJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
        }
        val keyIssuerResponseData = KeyIssuerResponseData(
            id = "d229e28b-f881-4945-b0d8-09a413b04e00",
            encodedCertificate = "-----BEGIN CERTIFICATE-----\n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\n-----END CERTIFICATE-----\n",
            encryptionKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJBV0pyanZzbytJMW1yT0hybGFHanhGRFRZK2JveWRHMmw3RGRmS3hxYkJBenhRSzJRVjlzZEFCc0F0aDNFVWUya2lUUXhWMDlhWnpsd0xaY25oa1NhTT0iLCJ0eXBlIjoicHVibGljSERFS1AiLCJpYXQiOjE2MzcyMjg5NTJ9.kbJG4y9CEcyoexj78DHUBpNYHocNOYVJyS3nMxOMDXvDbSqIJbCwvIjQqI8y6zFTM4CEtBkdez5_6U-zZdQUJfUA_pX-Oz3CbQrjT3s7ERsz27xBsvu3uLAg4DH7Pjegxjnti3pFqQ1VHUe-5PGWQSlhaGvHzD47MrTp6nPvV3CKtJWa1DaC6tDNBKAI3fuP9NGA9pGmvJaJSNZPRIEhkheRCgVchd4GQIy-QyKd2hKGg6Eser_vSwEsN78Ogh8yTX4VVYnzsamOtw8PHkI9zRwEZzSxHkO59idKd3Lz-AVkEtjlotRTSKzyVBYwtwNo3wa6mAyBvwXuKHyota5U3Oyw6cn4CtCdQZNkF766-Vd30h39Ij-OgmTxLeQjQo5Xkc0H_BYl-_k8sWrPMTlqvUtVFvlyOEKj032xSlB-PGZOP922_vkIKGpHIgN_y-2gvbmu1OQISzdpPwt6BJHjE8538RthhnY9WDNKbyiVvzDOXQLnH7JcL-IGCLOFZI1zYNK2pTGzPUQPoE2Uk9A63Ma1AxnSFWyn2WtHhbKD4peWaUFmfGACR0NBKq9yd16GD-5dhXmwWB5eREwmCqt6iz3DODOAVG1pQcoWwzrBxhb-Bp92-7XwjvKmr_wYo6sNtjp-Slkw2tUrV000h3vCy_4fHGU9Qn2BNWNQQP_ys10",
            signingKeyJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJNV21NUkxTaWRYTHJVd0ZRcjlWd1lCKzN6ckFtblV4T0xUWDl5OWxOR21HTzMxU3JCWEFJOXdOZklhNzVicTlWNVRJa1VVd21xOE1ONG9HRDBveUduST0iLCJ0eXBlIjoicHVibGljSERTS1AiLCJpYXQiOjE2MzcyMjg5NTF9.mSxbgr8nBBTMO9TBiw6gaT9eIDQrm8vOgp_UN40CN0YLWmhDPFfVHM18Vbo_Gewhei4ynKGdiXlNRDvtjCDIJFjYPRCUmQMxheM7Dmne1tATa0418F9toA6-muM_kCnFabZ1yknSRfErzyiFk1hFrcePZ7v5sghbIlobLIPgxksExH1N36Iz9KViFCJr8joWW3OQgLqIAA4nBPHTb8zIjtYixuWZMube2hFpSDAYkITKXHxdyGWusi4S8GDgXgEBNJiUPIwAE3Bj-HV4I8L1dgBu-DNCX30kp5VkmIfuC9BvW0VjjpDNmlOUUGUEHDErfZd-uuzoB1W6DkMP_AW90efkSYuKACmte_F8YFNb8m0GS2VKAaQBRWk53m0MZRRqyGEWzW2A5ckgfsScYD6ibc2wkhBhh_o-Nff95OOaZ9r1SqB4swnPWPrEULC_1gHFIUzfewkCz_yp3BLhsm5N7A3SA8xE0Sw672hMo1xbb58A9O-hJR5koxcVpvCPp_tXCUQh_-rdM1mr3BarAqSVNtovI8f7uibSnK6R6afB3t-zRsmxtuqVhF3aDTULqqofD-DAS-SGX46egZ89WMKqpoozpBCq8av6kzrqmurJ0sq_baQ4hpMuAQpquR0ablNC2i-oqpC3wfWohZurGvcQYev58tlDjfLgPSEJzP21XRw"
        )
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, keyIssuerResponseData)
        doReturn(Completable.complete()).`when`(cryptoManager).assertKeyNotExpired(any())
        doReturn(Completable.complete()).`when`(cryptoManager).verifyKeyIssuerCertificate(any())

        cryptoManager.verifyDailyPublicKeyData(dailyPublicKeyData)
            .test()
            .await()
            .assertComplete()
    }

    private fun mockNetworkResponses(dailyPublicKeyResponseData: DailyPublicKeyResponseData, keyIssuerResponseData: KeyIssuerResponseData) {
        val lucaEndpointsV4 = Mockito.mock(LucaEndpointsV4::class.java)
        Mockito.doReturn(Single.just(dailyPublicKeyResponseData)).`when`(lucaEndpointsV4).dailyPublicKey
        Mockito.doReturn(Single.just(keyIssuerResponseData)).`when`(lucaEndpointsV4).getKeyIssuer(Mockito.anyString())
        Mockito.doReturn(Single.just(lucaEndpointsV4)).`when`(networkManager).getLucaEndpointsV4()
    }

    companion object {

        private const val ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY =
            "BAIDQ7/zTOcV+XXX5io9XZn1t4MUOAswVfZKd6Fpup/MwlNssx4mCEPcO34AIiV0TbL2ywOP3QoHs41cfvv7uTo="
        private const val ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY = "JwlHQ8w3GjM6T94PwgltA7PNvCk1xokk8HcqXG0CXBI="
        private const val ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY =
            "BIMFVAOglk1B4PIlpaVspeWeFwO5eUusqxFAUUDFNJYGpbp9iu0jRHQAipDTVgFSudcm9tF5kh4+wILrAm3vHWg="
        private const val ENCODED_SHARED_DH_SECRET = "cSPbpq56ygtUX0TayiRw0KJpaeoNS/3dcNljtndAXaE="
        private val DAILY_PUBLIC_KEY_RESPONSE_DATA = DailyPublicKeyResponseData(
            "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
        )
        private val KEY_ISSUER_RESPONSE_DATA = KeyIssuerResponseData(
            "d229e28b-f881-4945-b0d8-09a413b04e00",
            "-----BEGIN CERTIFICATE-----\n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\n-----END CERTIFICATE-----\n",
            "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJBV0pyanZzbytJMW1yT0hybGFHanhGRFRZK2JveWRHMmw3RGRmS3hxYkJBenhRSzJRVjlzZEFCc0F0aDNFVWUya2lUUXhWMDlhWnpsd0xaY25oa1NhTT0iLCJ0eXBlIjoicHVibGljSERFS1AiLCJpYXQiOjE2MzcyMjg5NTJ9.kbJG4y9CEcyoexj78DHUBpNYHocNOYVJyS3nMxOMDXvDbSqIJbCwvIjQqI8y6zFTM4CEtBkdez5_6U-zZdQUJfUA_pX-Oz3CbQrjT3s7ERsz27xBsvu3uLAg4DH7Pjegxjnti3pFqQ1VHUe-5PGWQSlhaGvHzD47MrTp6nPvV3CKtJWa1DaC6tDNBKAI3fuP9NGA9pGmvJaJSNZPRIEhkheRCgVchd4GQIy-QyKd2hKGg6Eser_vSwEsN78Ogh8yTX4VVYnzsamOtw8PHkI9zRwEZzSxHkO59idKd3Lz-AVkEtjlotRTSKzyVBYwtwNo3wa6mAyBvwXuKHyota5U3Oyw6cn4CtCdQZNkF766-Vd30h39Ij-OgmTxLeQjQo5Xkc0H_BYl-_k8sWrPMTlqvUtVFvlyOEKj032xSlB-PGZOP922_vkIKGpHIgN_y-2gvbmu1OQISzdpPwt6BJHjE8538RthhnY9WDNKbyiVvzDOXQLnH7JcL-IGCLOFZI1zYNK2pTGzPUQPoE2Uk9A63Ma1AxnSFWyn2WtHhbKD4peWaUFmfGACR0NBKq9yd16GD-5dhXmwWB5eREwmCqt6iz3DODOAVG1pQcoWwzrBxhb-Bp92-7XwjvKmr_wYo6sNtjp-Slkw2tUrV000h3vCy_4fHGU9Qn2BNWNQQP_ys10",
            "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJNV21NUkxTaWRYTHJVd0ZRcjlWd1lCKzN6ckFtblV4T0xUWDl5OWxOR21HTzMxU3JCWEFJOXdOZklhNzVicTlWNVRJa1VVd21xOE1ONG9HRDBveUduST0iLCJ0eXBlIjoicHVibGljSERTS1AiLCJpYXQiOjE2MzcyMjg5NTF9.mSxbgr8nBBTMO9TBiw6gaT9eIDQrm8vOgp_UN40CN0YLWmhDPFfVHM18Vbo_Gewhei4ynKGdiXlNRDvtjCDIJFjYPRCUmQMxheM7Dmne1tATa0418F9toA6-muM_kCnFabZ1yknSRfErzyiFk1hFrcePZ7v5sghbIlobLIPgxksExH1N36Iz9KViFCJr8joWW3OQgLqIAA4nBPHTb8zIjtYixuWZMube2hFpSDAYkITKXHxdyGWusi4S8GDgXgEBNJiUPIwAE3Bj-HV4I8L1dgBu-DNCX30kp5VkmIfuC9BvW0VjjpDNmlOUUGUEHDErfZd-uuzoB1W6DkMP_AW90efkSYuKACmte_F8YFNb8m0GS2VKAaQBRWk53m0MZRRqyGEWzW2A5ckgfsScYD6ibc2wkhBhh_o-Nff95OOaZ9r1SqB4swnPWPrEULC_1gHFIUzfewkCz_yp3BLhsm5N7A3SA8xE0Sw672hMo1xbb58A9O-hJR5koxcVpvCPp_tXCUQh_-rdM1mr3BarAqSVNtovI8f7uibSnK6R6afB3t-zRsmxtuqVhF3aDTULqqofD-DAS-SGX46egZ89WMKqpoozpBCq8av6kzrqmurJ0sq_baQ4hpMuAQpquR0ablNC2i-oqpC3wfWohZurGvcQYev58tlDjfLgPSEJzP21XRw"
        )
        private const val ENCODED_PUBLIC_KEY = "035ddf3ad7348a3b98024261a5a9888e2b9636d1b1d16efef01ad485e5f9248157"
        private const val ENCODED_PRIVATE_KEY = "57a7ab110b33b40996c09fc1d14ea55901831e9926d66b22ded4676e16e93fd0"
        val DAILY_KEY_NOT_EXPIRED = DailyPublicKeyData(
            id = 0,
            creationTimestamp = TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(8), // older than 7 days
            encodedPublicKey = ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
            issuerId = ""
        )

        @JvmStatic
        fun decodeSecret(encodedSecret: String): ByteArray {
            return fromBase64(encodedSecret).blockingGet()
        }
    }
}
