package de.culture4life.luca.ui

import android.content.Context
import androidx.annotation.StringRes
import de.culture4life.luca.R
import de.culture4life.luca.crypto.DailyKeyUnavailableException
import de.culture4life.luca.rollout.RolloutException
import de.culture4life.luca.util.ThrowableUtil.isCause
import de.culture4life.luca.util.ThrowableUtil.isNetworkError
import de.culture4life.luca.util.getMessagesFromThrowableAndCauses
import de.culture4life.luca.util.isHttpException
import de.culture4life.luca.util.isNetworkError
import io.reactivex.rxjava3.core.Completable
import java.net.HttpURLConnection
import javax.net.ssl.SSLPeerUnverifiedException

data class ViewError(
    var title: String,
    val description: String,
    val resolveLabel: String? = null,
    val removeWhenShown: Boolean = false,
    val canBeShownAsNotification: Boolean = false,
    val isExpected: Boolean = false,
    val isCancelable: Boolean = true,
    val resolveAction: Completable? = null,
    val cause: Throwable? = null
) {
    val isResolvable: Boolean
        get() = resolveAction != null

    class Builder(private val context: Context) {

        private var title: String? = null
        private var description: String? = null
        private var cause: Throwable? = null
        private var resolveLabel: String? = null
        private var resolveAction: Completable? = null
        private var removeWhenShown = false
        private var canBeShownAsNotification = false
        private var isExpected = false
        private var isCancelable = true

        fun withTitle(title: String) = apply { this.title = title }
        fun withTitle(@StringRes stringResource: Int) = apply { withTitle(context.getString(stringResource)) }
        fun withDescription(description: String?) = apply { this.description = description }
        fun withDescription(@StringRes stringResource: Int) = apply { withDescription(context.getString(stringResource)) }
        fun withResolveLabel(resolveLabel: String) = apply { this.resolveLabel = resolveLabel }
        fun withResolveLabel(@StringRes stringResource: Int) = withResolveLabel(context.getString(stringResource))
        fun withResolveAction(resolveAction: Completable) = apply { this.resolveAction = resolveAction }
        fun removeWhenShown() = apply { removeWhenShown = true }
        fun canBeShownAsNotification() = apply { canBeShownAsNotification = true }
        fun isExpected() = apply { isExpected = true }
        fun setExpected(isExpected: Boolean) = apply { this.isExpected = isExpected }
        fun setCancelable(isCancelable: Boolean) = apply { this.isCancelable = isCancelable }
        fun withCause(cause: Throwable) = apply {
            this.cause = cause
            if (title == null) title = createTitle(cause)
            if (description == null) description = createDescription(cause)
        }

        fun build(): ViewError {
            requireNotNull(title) { "No error title set" }
            requireNotNull(description) { "No error description set" }
            check(!(resolveAction != null && resolveLabel == null)) { "No resolve label set" }
            return ViewError(
                title = title!!,
                description = description!!,
                resolveLabel = resolveLabel,
                removeWhenShown = removeWhenShown,
                canBeShownAsNotification = canBeShownAsNotification,
                isExpected = isExpected,
                isCancelable = isCancelable,
                resolveAction = resolveAction,
                cause = cause
            )
        }

        private fun createTitle(throwable: Throwable): String {
            return when {
                throwable is NullPointerException -> context.getString(R.string.error_generic_title)
                isNetworkError(throwable) -> context.getString(R.string.error_no_internet_connection_title)
                isCause(DailyKeyUnavailableException::class.java, throwable) -> context.getString(R.string.error_no_daily_key_available_title)
                throwable is SSLPeerUnverifiedException -> context.getString(R.string.error_certificate_pinning_title)
                isCause(RolloutException::class.java, throwable) -> context.getString(R.string.error_rollout_title)
                else -> {
                    val type = throwable.javaClass.simpleName
                    context.getString(R.string.error_specific_title, type)
                }
            }
        }

        private fun createDescription(throwable: Throwable): String {
            return when {
                throwable.isNetworkError() -> context.getString(R.string.error_no_internet_connection_description)
                throwable.isHttpException(
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    HttpURLConnection.HTTP_BAD_GATEWAY,
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    HttpURLConnection.HTTP_GATEWAY_TIMEOUT
                ) -> context.getString(R.string.error_http_server_error_description)
                isCause(DailyKeyUnavailableException::class.java, throwable) -> context.getString(R.string.error_no_daily_key_available_description)
                throwable is SSLPeerUnverifiedException -> context.getString(R.string.error_certificate_pinning_description)
                isCause(RolloutException::class.java, throwable) -> context.getString(R.string.error_rollout_description)
                else -> {
                    val description = throwable.getMessagesFromThrowableAndCauses()
                    if (description != null) {
                        context.getString(R.string.error_specific_description, description)
                    } else {
                        context.getString(R.string.error_generic_description)
                    }
                }
            }
        }
    }
}
