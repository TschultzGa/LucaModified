package de.culture4life.luca.network

import de.culture4life.luca.LucaUnitTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LucaApiExceptionTest : LucaUnitTest() {

    @Test
    fun `Error message parsed from API response if provided`() {
        val response = Response.error<String>(500, VALID_ERROR_RESPONSE_JSON.toResponseBody(MEDIA_TYPE))
        val exception = LucaApiException(DEFAULT_MESSAGE, HttpException(response))
        assertEquals("An internal server error occurred", exception.message)
    }

    @Test
    fun `Error message from cause used if no API response error provided`() {
        val response = Response.error<String>(500, INVALID_ERROR_RESPONSE_HTML.toResponseBody(MEDIA_TYPE))
        val exception = LucaApiException(DEFAULT_MESSAGE, HttpException(response))
        assertThat(exception.message).startsWith("HTTP 500")
    }

    @Test
    fun `Error message from constructor used if no API response error or cause message provided`() {
        val exception = LucaApiException(DEFAULT_MESSAGE, IOException())
        assertEquals(DEFAULT_MESSAGE, exception.message)
    }

    companion object {
        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_MESSAGE = "Something went wrong"
        private const val VALID_ERROR_RESPONSE_JSON = "{\n" +
            "  \"statusCode\": 500,\n" +
            "  \"error\": \"Internal Server Error\",\n" +
            "  \"message\": \"An internal server error occurred\",\n" +
            "  \"stack\": \"Error: Request failed with status code 500\\n    at createError (/app/node_modules/axios/lib/core/createError.js:16:15)\\n    at settle (/app/node_modules/axios/lib/core/settle.js:17:12)\\n    at IncomingMessage.handleStreamEnd (/app/node_modules/axios/lib/adapters/http.js:312:11)\\n    at IncomingMessage.emit (node:events:532:35)\\n    at endReadableNT (node:internal/streams/readable:1346:12)\\n    at processTicksAndRejections (node:internal/process/task_queues:83:21)\"\n" +
            "}"
        private const val INVALID_ERROR_RESPONSE_HTML = "<!DOCTYPE html><html lang=\"de-DE\" class=\"no-js\"><head>..."
    }
}
