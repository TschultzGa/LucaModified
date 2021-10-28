package de.culture4life.luca.checkin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static de.culture4life.luca.crypto.CryptoManagerTest.decodeSecret;
import static de.culture4life.luca.history.HistoryManager.SHARE_DATA_DURATION;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.crypto.CryptoManager;
import io.reactivex.rxjava3.core.Single;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class CheckInManagerTest extends LucaUnitTest {

    private static final String ENCODED_TRACE_ID = "Z0aw+vjwazzQHj21PxmWTQ==";
    private static final String ENCODED_TRACE_SECRET = "dZrDSp83PCcVL5ZvsJypwA==";
    private static final UUID USER_ID = UUID.fromString("02fb635c-f6a5-48eb-8379-a83d611618f2");

    private CheckInManager checkInManager;

    @Before
    public void setup() {
        checkInManager = spy(getInitializedManager(application.getCheckInManager()));
    }


    @Test
    public void getTraceIdWrapper_generateNew_isNotEmpty() {
        doReturn(Single.just(decodeSecret(ENCODED_TRACE_SECRET)))
                .when(checkInManager).getCurrentTracingSecret();
        checkInManager.getTraceIdWrapper(USER_ID)
                .map(traceIdWrapper -> traceIdWrapper.getTraceId().length > 0)
                .test()
                .assertValue(true);
    }

    @Test
    public void getTraceIdWrappers_afterGenerateTraceId_isNotEmpty() {
        doReturn(Single.just(decodeSecret(ENCODED_TRACE_SECRET)))
                .when(checkInManager).getCurrentTracingSecret();
        checkInManager.generateTraceId(USER_ID, 1601481600L)
                .ignoreElement()
                .andThen(checkInManager.getTraceIdWrappers())
                .toList()
                .test()
                .assertValueCount(1);
    }

    @Test
    public void getTraceIdWrappers_afterDeleteTraceData_isEmpty() {
        checkInManager.generateTraceId(USER_ID, 1601481600L)
                .ignoreElement()
                .andThen(checkInManager.deleteTraceData())
                .andThen(checkInManager.getTraceIdWrappers())
                .toList()
                .test()
                .assertEmpty();
    }

    @Test
    public void generateTraceId() {
        doReturn(Single.just(decodeSecret(ENCODED_TRACE_SECRET)))
                .when(checkInManager).getCurrentTracingSecret();
        checkInManager.generateTraceId(USER_ID, 1601481600L)
                .flatMap(CryptoManager::encodeToString)
                .test()
                .assertValue(ENCODED_TRACE_ID);
    }

    @Test
    public void generateRecentStartOfDayTimestamps_validDuration_emitsCorrectTimestamps() {
        List<Long> test = checkInManager.generateRecentStartOfDayTimestamps(SHARE_DATA_DURATION).toList().blockingGet();
        for (int i = 0; i < test.size() - 1; i++) {
            long diff = test.get(i) - test.get(i + 1);
            Assert.assertEquals(diff, TimeUnit.DAYS.toMillis(1));
        }
    }

    @Test
    public void generateScannerEphemeralKeyPair_publicKey_usesEc() {
        checkInManager.generateScannerEphemeralKeyPair()
                .map(keyPair -> keyPair.getPublic().getAlgorithm())
                .test()
                .assertValue("EC");
    }

    @Test
    public void isSelfCheckInUrl_validUrls_returnsTrue() {
        assertTrue(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f"));
        assertTrue(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f#e30"));
        assertTrue(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/512875cb-17e6-4dad-ac62-3e792d94e03f#e30/CWA1/CiRmY2E..."));
    }

    @Test
    public void isSelfCheckInUrl_invalidUrls_returnsFalse() {
        assertFalse(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/"));
        assertFalse(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/setup"));
        assertFalse(CheckInManager.isSelfCheckInUrl("https://app.luca-app.de/webapp/testresult/#eyJ0eXAi..."));
    }

}