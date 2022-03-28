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
        return throwableClass.isInstance(throwable) ||
            isNotRxJavaAssemblyException(throwable) && (throwable != null && isCause(throwableClass, throwable.cause))
    }

    @JvmStatic
    fun isNetworkError(throwable: Throwable?): Boolean {
        return when {
            isCause(UnknownHostException::class.java, throwable) ||
                isCause(ConnectException::class.java, throwable) ||
                isCause(SocketTimeoutException::class.java, throwable) ||
                isCause(SocketException::class.java, throwable) -> true
            else -> false
        }
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

fun Throwable.isHttpException(vararg expectedStatusCodes: Int): Boolean {
    return NetworkManager.isHttpException(this, *expectedStatusCodes.toTypedArray())
}
