package de.culture4life.luca.document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static de.culture4life.luca.document.Document.MAXIMUM_VACCINATION_VAILIDITY;

import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.util.TimeUtil;

public class DocumentTest extends LucaUnitTest {

    private Document document;

    @Before
    public void setUp() {
        document = new Document();
    }

    @Test
    public void getValidityStartTimestamp_vaccination_isLaterThanTestValidFrom() {
        document.setType(Document.TYPE_VACCINATION);
        document.setOutcome(Document.OUTCOME_FULLY_IMMUNE);
        long vaccinationValidFrom = document.getValidityStartTimestamp();

        document.setType(Document.TYPE_PCR);
        long testValidFrom = document.getValidityStartTimestamp();

        assertTrue(testValidFrom < vaccinationValidFrom);
    }

    @Test
    public void getValidityStartTimestamp_vaccination_expectedDaysSinceTesting() {
        document.setType(Document.TYPE_VACCINATION);
        document.setOutcome(Document.OUTCOME_FULLY_IMMUNE);
        document.setTestingTimestamp(1626220800000L); // Wed Jul 14 2021 02:00:00 GMT+0200
        assertEquals(1627516800000L, document.getValidityStartTimestamp()); // Thu Jul 29 2021 02:00:00 GMT+0200
    }

    @Test
    public void getExpirationTimestamp_differentTestingTimestamps_differentExpirationTimestamps() {
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(2));
        long firstExpirationTimestamp = document.getExpirationTimestamp();

        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(1));
        long secondExpirationTimestamp = document.getExpirationTimestamp();

        assertTrue(firstExpirationTimestamp < secondExpirationTimestamp);
    }

    @Test
    public void getExpirationTimestamp_differentTypes_differentExpirationTimestamps() {
        document.setType(Document.TYPE_FAST);
        long fastExpirationTimestamp = document.getExpirationTimestamp();

        document.setType(Document.TYPE_PCR);
        long pcrExpirationTimestamp = document.getExpirationTimestamp();

        assertTrue(fastExpirationTimestamp < pcrExpirationTimestamp);
    }

    @Test
    public void getExpirationDuration_typeFast_isTwoDays() {
        assertEquals(TimeUnit.DAYS.toMillis(2), document.getExpirationDuration(Document.TYPE_FAST));
    }

    @Test
    public void getExpirationDuration_typeNegativePcr_isThreeDays() {
        document.setOutcome(Document.OUTCOME_NEGATIVE);
        assertEquals(TimeUnit.DAYS.toMillis(3), document.getExpirationDuration(Document.TYPE_PCR));
    }

    @Test
    public void getExpirationDuration_typePositivePcr_isSixMonths() {
        document.setOutcome(Document.OUTCOME_POSITIVE);
        assertEquals(TimeUnit.DAYS.toMillis(30 * 6), document.getExpirationDuration(Document.TYPE_PCR));
    }

    @Test
    public void getExpirationDuration_typeVaccination_isOneYear() {
        long oneYearDurationFromTesting = Instant
                .ofEpochMilli(document.getTestingTimestamp())
                .toDateTime(DateTimeZone.UTC)
                .plus(MAXIMUM_VACCINATION_VAILIDITY)
                .getMillis() - document.getTestingTimestamp();
        assertEquals(oneYearDurationFromTesting, document.getExpirationDuration(Document.TYPE_VACCINATION));
    }

    @Test
    public void isValidRecovery_forPositivePcrTest_isTrue() {
        document.setType(Document.TYPE_PCR);
        document.setOutcome(Document.OUTCOME_POSITIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(16));
        assertTrue(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forRecoveryCertificate_isTrue() {
        document.setType(Document.TYPE_RECOVERY);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(30 * 3));
        assertTrue(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forNegativePcrTest_isFalse() {
        document.setType(Document.TYPE_PCR);
        document.setOutcome(Document.OUTCOME_NEGATIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(16));
        assertFalse(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forPositiveRapidTest_isFalse() {
        document.setType(Document.TYPE_FAST);
        document.setOutcome(Document.OUTCOME_POSITIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(16));
        assertFalse(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forTooNewPositivePcrTest_isFalse() {
        document.setType(Document.TYPE_PCR);
        document.setOutcome(Document.OUTCOME_POSITIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(5));
        assertFalse(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forTooOldPositivePcrTest_isFalse() {
        document.setType(Document.TYPE_PCR);
        document.setOutcome(Document.OUTCOME_POSITIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() - TimeUnit.DAYS.toMillis(30 * 7));
        assertFalse(document.isValidRecovery());
    }

    @Test
    public void isValidRecovery_forFuturePositivePcrTest_isFalse() {
        document.setType(Document.TYPE_PCR);
        document.setOutcome(Document.OUTCOME_POSITIVE);
        document.setTestingTimestamp(TimeUtil.getCurrentMillis() + TimeUnit.DAYS.toMillis(1));
        assertFalse(document.isValidRecovery());
    }
}
