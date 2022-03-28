package de.culture4life.luca.testtools.rules

import androidx.test.core.app.ApplicationProvider
import de.culture4life.luca.LucaApplication
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Provides and configures usage of a mock server.
 */
class MockWebServerRule : BaseHookingTestRule() {

    private val mockServer = MockWebServer()
    val mockResponse = UrlDispatcher()

    override fun beforeTest() {
        mockServer.dispatcher = mockResponse
        mockServer.start()

        injectMockServerAddress()
    }

    override fun afterTest() {
        mockServer.shutdown()
    }

    private fun injectMockServerAddress() {
        // Should be done as early as possible, before any initialization happen to mock them all.
        // You will see calls to app-dev.luca.de instead of localhost when it was to late.
        ApplicationProvider.getApplicationContext<LucaApplication>().networkManager
            .overrideServerAddress(mockServer.url("/"))
    }

    fun assertPostRequest(path: String) {
        val lastRequest = requireNextRequest()
        assertThat(lastRequest.path).isEqualTo(path)
        assertThat(lastRequest.method).isEqualTo("POST")
    }

    fun assertGetRequest(vararg paths: String) {
        val lastRequest = requireNextRequest()
        assert(paths.contains(lastRequest.path))
        assertThat(lastRequest.method).isEqualTo("GET")
    }

    // TODO Very flaky approach because it just give the next queued request.
    //  Instead we should iterate through the collection for matching requests.
    //  Just how WireMock does it, but integrating WireMock is much more effort than MockWebServer.
    private fun requireNextRequest() = mockServer.takeRequest(50, TimeUnit.MILLISECONDS)
        ?: throw IllegalStateException("No more requests recorded.")
}

/**
 * Mock directly specific http requests instead of just queueing the responses in a specific order.
 */
class UrlDispatcher : Dispatcher() {
    private val map = mutableMapOf<String, MockResponse>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        return map.getOrDefault(request.path, MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_IMPLEMENTED))
    }

    fun put(path: String, mock: MockResponse.() -> Unit) {
        map[path] = MockResponse().also(mock)
    }
}
