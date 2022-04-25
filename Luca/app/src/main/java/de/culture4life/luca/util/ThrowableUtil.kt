package de.culture4life.luca.util

import de.culture4life.luca.network.NetworkManager
import hu.akarnokd.rxjava3.debug.RxJavaAssemblyException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ThrowableUtil {

    @JvmStatic
    fun isCause(throwableClass: Class<out Throwable>, throwable: Throwable?): Boolean {
        return getCauseIfAvailable(throwableClass, throwable) != null
    }

    @JvmStatic
    fun getCauseIfAvailable(throwableClass: Class<out Throwable>, throwable: Throwable?): Throwable? {
        return when {
            throwable == null -> null
            throwableClass.isInstance(throwable) -> throwable
            isNotRxJavaAssemblyException(throwable) -> getCauseIfAvailable(throwableClass, throwable.cause)
            else -> null
        }
    }

    @JvmStatic
    fun isNetworkError(throwable: Throwable?): Boolean {
        return when {
            isCause(UnknownHostException::class.java, throwable) -> true
            isCause(ConnectException::class.java, throwable) -> true
            isCause(SocketTimeoutException::class.java, throwable) -> true
            isCause(SocketException::class.java, throwable) -> true
            else -> false
        }
    }

    @JvmStatic
    fun getMessagesFromThrowableAndCauses(throwable: Throwable): String? {
        if (throwable is RxJavaAssemblyException) {
            // these don't have any meaningful messages
            return null
        }
        var message = throwable.localizedMessage
        if (message == null) {
            message = throwable.javaClass.simpleName
        }
        if (!message!!.endsWith(".")) {
            message += "."
        }
        if (throwable.cause != null) {
            val causeMessage = getMessagesFromThrowableAndCauses(throwable.cause!!)
            if (causeMessage != null) {
                message += " $causeMessage"
            }
        }
        return message
    }

    /**
     * Check if given Throwable is RxJavaAssemblyException,
     * which is only available when RxJavaAssemblyTracking.enable() == true in debug builds.
     *
     * Check is necessary because iterations through RxJavaAssemblyExceptions can/will lead to
     * infinite loops.
     */
    private fun isNotRxJavaAssemblyException(throwable: Throwable?): Boolean {
        return throwable !is RxJavaAssemblyException
    }
}

fun Throwable.isCause(throwableClass: Class<out Throwable>): Boolean {
    return ThrowableUtil.isCause(throwableClass, this)
}

fun Throwable.getCauseIfAvailable(throwableClass: Class<out Throwable>): Throwable? {
    return ThrowableUtil.getCauseIfAvailable(throwableClass, this)
}

fun Throwable.getMessagesFromThrowableAndCauses(): String? {
    return ThrowableUtil.getMessagesFromThrowableAndCauses(this)
}

fun Throwable.isNetworkError(): Boolean {
    return ThrowableUtil.isNetworkError(this)
}

fun Throwable.isHttpException(vararg expectedStatusCodes: Int): Boolean {
    return NetworkManager.isHttpException(this, *expectedStatusCodes)
}
