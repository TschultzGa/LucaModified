package de.culture4life.luca.checkin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static de.culture4life.luca.crypto.CryptoManagerTest.decodeSecret;
import static de.culture4life.luca.history.HistoryManager.SHARE_DATA_DURATION;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class CheckInManagerTest extends LucaUnitTest {

    private static final String ENCODED_TRACE_ID = "Z0aw+vjwazzQHj21PxmWTQ==";
    private static final String ENCODED_TRACE_SECRET = "dZrDSp83PCcVL5ZvsJypwA==";
    private static final UUID USER_ID = UUID.fromString("02fb635c-f6a5-48eb-8379-a83d611618f2");

    private CheckInManager checkInManager;

    @Mock
    private GenuinityManager genuinityManager;

    private AutoCloseable mockitoCloseable;

    @Before
    public void setup() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        when(genuinityManager.initialize(any())).thenReturn(Completable.complete());
        checkInManager = spy(
                getInitializedManager(
                        new CheckInManager(
                                application.getPreferencesManager(),
                                application.getNetworkManager(),
                                application.getGeofenceManager(),
                                application.getLocationManager(),
                                application.getHistoryManager(),
                                application.getCryptoManager(),
                                application.getNotificationManager(),
                                genuinityManager
                        )
                )
        );
    }

    @After
    public void after() throws Exception {
        mockitoCloseable.close();
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
                .andThen(checkInManager.deleteUnusedTraceData())
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
                .flatMap(SerializationUtil::toBase64)
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
    public void getCurrentCheckInDuration_withServerTimeOffset_emitsCorrectDuration() throws InterruptedException {
        // Given
        // Local time is 10 minutes after server time and checked in for 20 minutes after server time
        ZonedDateTime serverCheckinTime = LocalDateTime.parse("1993-12-20T10:00").atZone(ZoneOffset.UTC);
        ZonedDateTime localTime = serverCheckinTime.plusMinutes(20);
        long localTimeOffsetToServer = TimeUnit.MINUTES.toMillis(10);
        long expectedCheckinDuration = TimeUnit.MINUTES.toMillis(10);
        CheckInData checkInData = mock(CheckInData.class);

        TimeUtil.setClock(Clock.fixed(Instant.ofEpochMilli(localTime.toInstant().toEpochMilli()), ZoneOffset.UTC));
        doReturn(serverCheckinTime.toInstant().toEpochMilli()).when(checkInData).getTimestamp();
        doReturn(Single.just(localTimeOffsetToServer)).when(genuinityManager).getOrFetchOrRestoreServerTimeOffset();
        doReturn(Maybe.just(checkInData)).when(checkInManager).getCheckInDataIfAvailable();

        // When
        TestObserver<Long> getting = checkInManager.getCurrentCheckInDuration().test();

        // Then
        getting.await().assertNoErrors().assertValue(expectedCheckinDuration);
        TimeUtil.setClock(Clock.systemUTC());
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