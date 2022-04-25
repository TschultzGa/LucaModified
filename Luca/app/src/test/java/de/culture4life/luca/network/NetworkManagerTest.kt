package de.culture4life.luca.network

import de.culture4life.luca.LucaUnitTest
import junit.framework.TestCase
import okhttp3.Request
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import retrofit2.HttpException
import java.net.HttpURLConnection

class NetworkManagerTest : LucaUnitTest() {
    @Test
    fun useCdn_cacheableEndpoints_returnsTrue() {
        val urls = arrayOf(
            "https://app.luca-app.de/api/v4/notifications/traces",
            "https://app.luca-app.de/api/v4/notifications/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5",
            "https://app.luca-app.de/api/v4/healthDepartments",
            "https://app.luca-app.de/api/v4/healthDepartments/9a5c8715-2810-4e17-a3c9-0c8190507dd5"
        )
        for (url in urls) {
            val request: Request = Request.Builder().url(url).build()
            Assert.assertTrue(NetworkManager.useCdn(request))
        }
    }

    @Test
    fun useCdn_nonCacheableEndpoints_returnsFalse() {
        val urls = arrayOf(
            "https://app.luca-app.de/api/v3/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5",
            "https://app.luca-app.de/api/v4/locations/traces/9a5c8715-2810-4e17-a3c9-0c8190507dd5"
        )
        for (url in urls) {
            val request: Request = Request.Builder().url(url).build()
            TestCase.assertFalse(NetworkManager.useCdn(request))
        }
    }

    @Test
    fun replaceHostWithCdn_validProductionRequest_replacesHost() {
        val expected = "https://data.luca-app.de/api/v4/notifications"
        val request: Request = Request.Builder().url("https://app.luca-app.de/api/v4/notifications").build()
        assertEquals(expected, NetworkManager.replaceHostWithCdn(request).url.toString())
    }

    @Test
    fun replaceHostWithCdn_validStagingRequest_replacesHost() {
        val expected = "https://data-dev.luca-app.de/api/v4/notifications"
        val request: Request = Request.Builder().url("https://app-dev.luca-app.de/api/v4/notifications").build()
        assertEquals(expected, NetworkManager.replaceHostWithCdn(request).url.toString())
    }

    @Test
    fun isHttpException_httpExceptionWithMatchingCode_returnsTrue() {
        val exception = Mockito.mock(HttpException::class.java)
        Mockito.`when`(exception.code()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN)
        Assert.assertTrue(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN))
        Assert.assertTrue(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN))
    }

    @Test
    fun isHttpException_httpExceptionWithNonMatchingCode_returnsFalse() {
        val exception = Mockito.mock(HttpException::class.java)
        Mockito.`when`(exception.code()).thenReturn(HttpURLConnection.HTTP_BAD_GATEWAY)
        TestCase.assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN))
        TestCase.assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN))
    }

    @Test
    fun isHttpException_differentException_returnsFalse() {
        val exception = Exception()
        TestCase.assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_FORBIDDEN))
        TestCase.assertFalse(NetworkManager.isHttpException(exception, HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN))
    }
}
