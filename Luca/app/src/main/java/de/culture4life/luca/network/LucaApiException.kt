package de.culture4life.luca.network

import de.culture4life.luca.network.pojo.ErrorResponseData
import de.culture4life.luca.util.deserializeFromJson
import retrofit2.HttpException
import timber.log.Timber

/**
 * Attempts to extract the network response body from the cause and
 * parses it to get the error message provided by the luca API backend.
 */
class LucaApiException(message: String? = null, cause: Throwable? = null) : Exception(getExceptionMessage(message, cause), cause) {

    constructor(cause: Throwable) : this(null, cause)

    companion object {

        fun getErrorResponseData(throwable: Throwable?): ErrorResponseData? {
            if (throwable == null || throwable !is HttpException) {
                return null
            }
            return try {
                throwable.response()
                    ?.errorBody()
                    ?.string()
                    ?.deserializeFromJson(ErrorResponseData::class.java)
            } catch (exception: Throwable) {
                Timber.w("Unable to parse error response data: $exception")
                null
            }
        }

        fun getExceptionMessage(errorResponseData: ErrorResponseData?): String? {
            return errorResponseData?.message // maybe also add status code if desired
        }

        fun getExceptionMessage(message: String? = null, cause: Throwable?): String? {
            val errorResponseData = getErrorResponseData(cause)
            val responseMessage = getExceptionMessage(errorResponseData) ?: cause?.message
            return responseMessage ?: message
        }
    }
}
