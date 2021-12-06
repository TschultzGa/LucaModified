package de.culture4life.luca.crypto;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static de.culture4life.luca.crypto.AsymmetricCipherProviderTest.decodePrivateKey;
import static de.culture4life.luca.crypto.AsymmetricCipherProviderTest.decodePublicKey;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.genuinity.NoGenuineTimeException;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.endpoints.LucaEndpointsV4;
import de.culture4life.luca.network.pojo.DailyPublicKeyResponseData;
import de.culture4life.luca.network.pojo.KeyIssuerResponseData;
import de.culture4life.luca.preference.PreferencesManager;
import io.reactivex.rxjava3.core.Single;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class CryptoManagerTest extends LucaUnitTest {


    private static final String ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY = "BAIDQ7/zTOcV+XXX5io9XZn1t4MUOAswVfZKd6Fpup/MwlNssx4mCEPcO34AIiV0TbL2ywOP3QoHs41cfvv7uTo=";
    private static final String ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY = "JwlHQ8w3GjM6T94PwgltA7PNvCk1xokk8HcqXG0CXBI=";
    private static final String ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY = "BIMFVAOglk1B4PIlpaVspeWeFwO5eUusqxFAUUDFNJYGpbp9iu0jRHQAipDTVgFSudcm9tF5kh4+wILrAm3vHWg=";
    private static final String ENCODED_SHARED_DH_SECRET = "cSPbpq56ygtUX0TayiRw0KJpaeoNS/3dcNljtndAXaE=";


    private static final DailyPublicKeyResponseData DAILY_PUBLIC_KEY_RESPONSE_DATA = new DailyPublicKeyResponseData(
            "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoicHVibGljRGFpbHlLZXkiLCJpc3MiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJrZXlJZCI6MjIsImtleSI6IkJONjhVbzB3aWVIOGNHT3NjcHNXa29yaEQrUklBTVpwR2NKK05ub2hmV0Z3K2lFU1k1b2J1aWR6T1ZWaWg1Mjk4ME5vMVNuMy9JTlpmTG9iZE5jQ0ViOD0iLCJpYXQiOjE2Mzc5MjM4MDN9.BrziQL1_wIvb5hPoyERkIzBvrR0QkKDVdn5qHXvFx-ILbcd6lk3xGbxp6bZMeKKGRpntRdRYCRl1RmCiUtM12g"
    );

    private static final KeyIssuerResponseData KEY_ISSUER_RESPONSE_DATA = new KeyIssuerResponseData(
            "d229e28b-f881-4945-b0d8-09a413b04e00",
            "-----BEGIN CERTIFICATE-----\nMIIF8jCCA9qgAwIBAgIUNraRTy+ykuT/pXzk+DfiBqHaPsEwDQYJKoZIhvcNAQEN\nBQAwbTELMAkGA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UEBxMGQmVy\nbGluMREwDwYDVQQKEwhsdWNhIERldjEpMCcGA1UEAxMgbHVjYSBEZXYgQ2x1c3Rl\nciBJbnRlcm1lZGlhdGUgQ0EwHhcNMjEwNzA5MTgxODAwWhcNMjIwNzA5MTgxODAw\nWjCBgTELMAkGA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UEBxMGQmVy\nbGluMREwDwYDVQQKEwhsdWNhIERldjEmMCQGA1UEAxMdRGV2IENsdXN0ZXIgSGVh\nbHRoIERlcGFydG1lbnQxFTATBgNVBAUTDENTTTAyNjA3MDkzOTCCAiIwDQYJKoZI\nhvcNAQEBBQADggIPADCCAgoCggIBAKow1660WFqNEgMpFaRqXOLgw8bIx4h8Zttk\nhWafkOCbNLW93Dlu7L+yvPzmWTXJ97pjIA4zABljJ17yh/K+7R2QjMWIFirHXbli\nOyn+maymTMrYAgb73QUCfzSBoTW9wGglmJMvpYW/uFNB+yFM/BemdR5CKtoKFtjY\nScIBbTfqrtZp8x815X6J0Ts5Iy0ltQKRQLrmq3CvDVCZnhzyC6LYyfAPTrSYunac\nYOWpyg0q9OXYqCskEGnuQN7ypMAbw9ku6hhdNmfKci+pO47Yy2IUcSa7ViAe9psU\nmEK8slkAtaKo0PoAZhCM4Rso2Ml6ah4xyyvloyFgzpyuZjWLyQK5So0Dv4uBUhXn\nY7ha5a2Ypxv7Qnv0AV8mUVfSRDM7FGRiO09v/S+8SJ+iszFQz3VxT6Nhp3cBhgz4\nplSokoLW+03efIiJOm5mQUx/5h1CQdAynbMJFiHa2DLRyOj2RDN9m8Rwo5nOWsVU\nF7M9N7zPwtHyRnTxa9FLb2xUytzEykibarTzcI7QqjJdALuxIvKeHnWT70LC8TCX\nMfIFh7Z6ZojXQTvfrJKeCtpRv8rBKmU4/GSIzDOH7vq5CLHnppj2ZXuypECYoWX7\nqoyvy8lxk0bYAGk/hndo9FzPLKWvnCmxovg3sMCtfG7Pt/006mZFFzhDoDULzKMd\nzOtChvEhAgMBAAGjdTBzMA4GA1UdDwEB/wQEAwIEsDATBgNVHSUEDDAKBggrBgEF\nBQcDAjAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQ2RdKX3FHzxyFgUsKV/8jnsB2o\n9jAfBgNVHSMEGDAWgBRMdbAGNCq//hXC9wYRlmcqAit07zANBgkqhkiG9w0BAQ0F\nAAOCAgEASlhUUeuZQAXabDqihPYeIAu5Ok3VhVtI2uEz1vlq20p7Ri3KQHUDFPu3\nwSELUr5rjmUhwDdB8Xsx9D+T+WzGKznIbx3m805Mp3ExDZ7qqyRbmWTE/6mi6R5A\nrGOzVtxkpbk0uukASRUf/PIDFasKo2XKkqJkNW905fSAncrRvQQBeIJQvK0HF2Pj\nv3n7Zxl2y+vT8oqSsoTfB+9IWJMtecHMjqe8qj3GB/uPyNYcuHi0/o3QW2wQB1Xn\nEeffrAjGk669gGUKuB2zcAcfBsQcfPQcRZEe7L+ExFHUklUujOeiMRFqm4qTlDyc\nabg1OiOaX48twR4CtXwuM40pQBOkj9e0NbhWmEWzP96rMtSRNlU/K4B2lbbJ3zWp\natBdAmv97xQd/3XC1SafxbtWXZo2s4AX7SzoQ4yIiae2RP1nC8/GxEApM6KXA5SD\nyxvtINpKU7cLAzP4cDMXc8/vDD7JOIzEwxRASo4pdQIaZBT+jRQ6BRRLxpYJyx2i\ng3vSCwENPv/Rpj4kobc46GsD/azmJ2ezMPVEEpJ63xFhEEHSNysGbq5JfrLHrQdB\n+bpxOFtliMb+QiLfiW4Lr+giq4OenJUb2TIPLjVnoJUjQLqQkrKIYccr0mXWzpUq\ntMk6sJ4QPw5+WeR/tceU56ekQzN/5ROeTTMtzAU8LENp+mpI42A=\n-----END CERTIFICATE-----\n",
            "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJBV0pyanZzbytJMW1yT0hybGFHanhGRFRZK2JveWRHMmw3RGRmS3hxYkJBenhRSzJRVjlzZEFCc0F0aDNFVWUya2lUUXhWMDlhWnpsd0xaY25oa1NhTT0iLCJ0eXBlIjoicHVibGljSERFS1AiLCJpYXQiOjE2MzcyMjg5NTJ9.kbJG4y9CEcyoexj78DHUBpNYHocNOYVJyS3nMxOMDXvDbSqIJbCwvIjQqI8y6zFTM4CEtBkdez5_6U-zZdQUJfUA_pX-Oz3CbQrjT3s7ERsz27xBsvu3uLAg4DH7Pjegxjnti3pFqQ1VHUe-5PGWQSlhaGvHzD47MrTp6nPvV3CKtJWa1DaC6tDNBKAI3fuP9NGA9pGmvJaJSNZPRIEhkheRCgVchd4GQIy-QyKd2hKGg6Eser_vSwEsN78Ogh8yTX4VVYnzsamOtw8PHkI9zRwEZzSxHkO59idKd3Lz-AVkEtjlotRTSKzyVBYwtwNo3wa6mAyBvwXuKHyota5U3Oyw6cn4CtCdQZNkF766-Vd30h39Ij-OgmTxLeQjQo5Xkc0H_BYl-_k8sWrPMTlqvUtVFvlyOEKj032xSlB-PGZOP922_vkIKGpHIgN_y-2gvbmu1OQISzdpPwt6BJHjE8538RthhnY9WDNKbyiVvzDOXQLnH7JcL-IGCLOFZI1zYNK2pTGzPUQPoE2Uk9A63Ma1AxnSFWyn2WtHhbKD4peWaUFmfGACR0NBKq9yd16GD-5dhXmwWB5eREwmCqt6iz3DODOAVG1pQcoWwzrBxhb-Bp92-7XwjvKmr_wYo6sNtjp-Slkw2tUrV000h3vCy_4fHGU9Qn2BNWNQQP_ys10",
            "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkMjI5ZTI4Yi1mODgxLTQ5NDUtYjBkOC0wOWE0MTNiMDRlMDAiLCJpc3MiOiI2NzY2ZWE3ZTQzMjI2MjI4ZDVhOGVjYjA5NWI2ZTQzZjI2NWE4MjZkIiwibmFtZSI6Ikdlc3VuZGhlaXRzYW10IERldiIsImtleSI6IkJNV21NUkxTaWRYTHJVd0ZRcjlWd1lCKzN6ckFtblV4T0xUWDl5OWxOR21HTzMxU3JCWEFJOXdOZklhNzVicTlWNVRJa1VVd21xOE1ONG9HRDBveUduST0iLCJ0eXBlIjoicHVibGljSERTS1AiLCJpYXQiOjE2MzcyMjg5NTF9.mSxbgr8nBBTMO9TBiw6gaT9eIDQrm8vOgp_UN40CN0YLWmhDPFfVHM18Vbo_Gewhei4ynKGdiXlNRDvtjCDIJFjYPRCUmQMxheM7Dmne1tATa0418F9toA6-muM_kCnFabZ1yknSRfErzyiFk1hFrcePZ7v5sghbIlobLIPgxksExH1N36Iz9KViFCJr8joWW3OQgLqIAA4nBPHTb8zIjtYixuWZMube2hFpSDAYkITKXHxdyGWusi4S8GDgXgEBNJiUPIwAE3Bj-HV4I8L1dgBu-DNCX30kp5VkmIfuC9BvW0VjjpDNmlOUUGUEHDErfZd-uuzoB1W6DkMP_AW90efkSYuKACmte_F8YFNb8m0GS2VKAaQBRWk53m0MZRRqyGEWzW2A5ckgfsScYD6ibc2wkhBhh_o-Nff95OOaZ9r1SqB4swnPWPrEULC_1gHFIUzfewkCz_yp3BLhsm5N7A3SA8xE0Sw672hMo1xbb58A9O-hJR5koxcVpvCPp_tXCUQh_-rdM1mr3BarAqSVNtovI8f7uibSnK6R6afB3t-zRsmxtuqVhF3aDTULqqofD-DAS-SGX46egZ89WMKqpoozpBCq8av6kzrqmurJ0sq_baQ4hpMuAQpquR0ablNC2i-oqpC3wfWohZurGvcQYev58tlDjfLgPSEJzP21XRw"
    );

    private CryptoManager cryptoManager;
    private NetworkManager networkManager;
    private GenuinityManager genuinityManager;

    @Before
    public void setup() {
        PreferencesManager preferencesManager = new PreferencesManager();
        networkManager = spy(new NetworkManager());
        genuinityManager = spy(new GenuinityManager(preferencesManager, networkManager));
        cryptoManager = spy(new CryptoManager(preferencesManager, networkManager, genuinityManager));
        cryptoManager.initialize(ApplicationProvider.getApplicationContext()).blockingAwait();

        Single<Long> getCurrentTime = Single.fromCallable(System::currentTimeMillis);
        doReturn(getCurrentTime).when(genuinityManager).fetchServerTime();
    }

    private void mockNetworkResponses(DailyPublicKeyResponseData dailyPublicKeyResponseData, KeyIssuerResponseData keyIssuerResponseData) {
        LucaEndpointsV4 lucaEndpointsV4 = Mockito.mock(LucaEndpointsV4.class);
        doReturn(Single.just(dailyPublicKeyResponseData)).when(lucaEndpointsV4).getDailyPublicKey();
        doReturn(Single.just(keyIssuerResponseData)).when(lucaEndpointsV4).getKeyIssuer(Mockito.anyString());
        doReturn(Single.just(lucaEndpointsV4)).when(networkManager).getLucaEndpointsV4();
    }

    @Test
    @Ignore("Fails on Jenkins only")
    public void updateDailyKeyPairPublicKey_validKeyPair_verifySuccess() throws InterruptedException {
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA);

        cryptoManager.updateDailyPublicKey()
                .test()
                .await()
                .assertComplete();
    }

    @Test
    @Ignore("Fails on Jenkins only")
    public void updateDailyKeyPairPublicKey_noGenuineTime_verifyFails() throws InterruptedException {
        mockNetworkResponses(DAILY_PUBLIC_KEY_RESPONSE_DATA, KEY_ISSUER_RESPONSE_DATA);
        doReturn(Single.just(false)).when(genuinityManager).isGenuineTime();

        cryptoManager.updateDailyPublicKey()
                .test()
                .await()
                .assertError(NoGenuineTimeException.class);
    }

    @Test
    public void assertKeyNotExpired_validKey_completes() {
        DailyPublicKeyData key = new DailyPublicKeyData(
                0,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6),
                ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
                ""
        );
        cryptoManager.assertKeyNotExpired(key)
                .test()
                .assertNoErrors()
                .assertComplete();
    }

    @Test
    public void assertKeyNotExpired_expiredKey_emitsError() {
        DailyPublicKeyData key = new DailyPublicKeyData(
                0,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8),
                ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
                ""
        );
        cryptoManager.assertKeyNotExpired(key)
                .test()
                .assertError(DailyKeyExpiredException.class);
    }

    @Test
    public void generateSharedDiffieHellmanSecret() {
        doReturn(Single.just(new DailyPublicKeyData(
                0,
                System.currentTimeMillis(),
                ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY,
                ""
        ))).when(cryptoManager).getDailyPublicKey();
        KeyPair userMasterKeyPair = new KeyPair(
                decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY),
                decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)
        );
        doReturn(Single.just(userMasterKeyPair))
                .when(cryptoManager).getGuestKeyPair();

        cryptoManager.generateSharedDiffieHellmanSecret()
                .flatMap(CryptoManager::encodeToString)
                .test()
                .assertValue(ENCODED_SHARED_DH_SECRET);
    }

    @Test
    public void encodeToString_decodeFromString_isSameAsInput() {
        String input = "abc123!§$%&/()=,.-*";
        CryptoManager.encodeToString(input.getBytes(StandardCharsets.UTF_8))
                .flatMap(CryptoManager::decodeFromString)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .test()
                .assertResult(input);
    }

    @Test
    public void encodeToString() {
        CryptoManager.encodeToString("AOU\nÄÖÜ".getBytes(StandardCharsets.UTF_8))
                .test()
                .assertResult("QU9VCsOEw5bDnA==");
    }

    @Test
    public void decodeFromString() {
        CryptoManager.decodeFromString("QU9VCsOEw5bDnA==")
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .test()
                .assertResult("AOU\nÄÖÜ");
    }

    public static byte[] decodeSecret(@NonNull String encodedSecret) {
        return CryptoManager.decodeFromString(encodedSecret).blockingGet();
    }

}