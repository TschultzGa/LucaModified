package de.culture4life.luca.network;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import java.net.HttpURLConnection;

import de.culture4life.luca.LucaUnitTest;
import okhttp3.Request;
import retrofit2.HttpException;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class NetworkManagerTest extends LucaUnitTest {

    @Test
    public void useCdn_cacheableEndpoints_returnsTrue() {
        String[] urls = new String[]{
                "https://app.luca-app.de/api/v4/notifications/traces",
                "https://app.luca-app.de/api/v4/notifications/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5",
                "https://app.luca-app.de/api/v4/healthDepartments",
                "https://app.luca-app.de/api/v4/healthDepartments/9a5c8715-2810-4e17-a3c9-0c8190507dd5"

        };
        for (String url : urls) {
            Request request = new Request.Builder().url(url).build();
            assertTrue(NetworkManager.useCdn(request));
        }
    }

    @Test
    public void useCdn_nonCacheableEndpoints_returnsFalse() {
        String[] urls = new String[]{
                "https://app.luca-app.de/api/v3/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5",
                "https://app.luca-app.de/api/v4/locations/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5"
        };
        for (String url : urls) {
            Request request = new Request.Builder().url(url).build();
            assertFalse(NetworkManager.useCdn(request));
        }
    }

    @Test
    public void replaceHostWithCdn_validProductionRequest_replacesHost() {
        String expected = "https://data.luca-app.de/api/v4/notifications";
        Request request = new Request.Builder().url("https://app.luca-app.de/api/v4/notifications").build();
        assertEquals(expected, NetworkManager.replaceHostWithCdn(request).url().toString());
    }

    @Test
    public void replaceHostWithCdn_validStagingRequest_replacesHost() {
        String expected = "https://data-dev.luca-app.de/api/v4/notifications";
        Request request = new Request.Builder().url("https://app-dev.luca-app.de/api/v4/notifications").build();
        assertEquals(expected, NetworkManager.replaceHostWithCdn(request).url().toString());
    }

    @Test
    public void isHttpException_httpExceptionWithMatchingCode_returnsTrue() {
        HttpException exception = Mockito.mock(HttpException.class);
        Mockito.when(exception.code()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        assertTrue(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN));
        assertTrue(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN));
    }

    @Test
    public void isHttpException_httpExceptionWithNonMatchingCode_returnsFalse() {
        HttpException exception = Mockito.mock(HttpException.class);
        Mockito.when(exception.code()).thenReturn(HttpURLConnection.HTTP_BAD_GATEWAY);
        assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN));
        assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN));
    }

    @Test
    public void isHttpException_differentException_returnsFalse() {
        Exception exception = new Exception();
        assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN));
        assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN));
    }

}