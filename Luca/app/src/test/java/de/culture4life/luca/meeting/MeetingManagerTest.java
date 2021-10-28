package de.culture4life.luca.meeting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import de.culture4life.luca.LucaUnitTest;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class MeetingManagerTest extends LucaUnitTest {

    private MeetingManager meetingManager;

    @Before
    public void setup() {
        meetingManager = getInitializedManager(application.getMeetingManager());
    }

    @Test
    public void generateMeetingEphemeralKeyPair_publicKey_usesEc() {
        meetingManager.generateMeetingEphemeralKeyPair()
                .map(keyPair -> keyPair.getPublic().getAlgorithm())
                .test()
                .assertValue("EC");
    }

    @Test
    public void isMeeting_validUrls_returnsTrue() {
        assertTrue(MeetingManager.isPrivateMeeting("https://app.luca-app.de/webapp/meeting/e4e3c...#e30"));
    }

    @Test
    public void isMeeting_invalidUrls_returnsFalse() {
        assertFalse(MeetingManager.isPrivateMeeting("https://app.luca-app.de/webapp"));
        assertFalse(MeetingManager.isPrivateMeeting("https://www.google.de"));
    }

}