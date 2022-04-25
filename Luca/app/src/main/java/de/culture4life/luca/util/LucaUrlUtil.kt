package de.culture4life.luca.util

import android.net.Uri
import java.util.*

object LucaUrlUtil {

    /**
     * Checks if given uri string contains correct host and path for luca Uris
     */
    @JvmStatic
    fun isLucaUrl(url: String): Boolean {
        val parsedUri = try {
            Uri.parse(url)
        } catch (throwable: Throwable) {
            return false
        }

        val isCorrectHost = parsedUri.host.orEmpty().let { host -> host == "luca-app.de" || host.endsWith(".luca-app.de") }
        val isCorrectPath = parsedUri.path.orEmpty().startsWith("/webapp")
        val isCorrectScheme = parsedUri.scheme.orEmpty().let { scheme -> scheme == "http" || scheme == "https" || scheme == "luca" }

        return isCorrectHost && isCorrectPath && isCorrectScheme
    }

    @JvmStatic
    fun isSelfCheckIn(url: String): Boolean {
        return isLucaUrl(url) && try {
            val parsedUri = Uri.parse(url)
            UUID.fromString(parsedUri.lastPathSegment)
            true
        } catch (throwable: Throwable) {
            false
        }
    }

    @JvmStatic
    fun isPrivateMeeting(url: String): Boolean {
        return isLucaUrl(url) && url.contains("/meeting")
    }

    @JvmStatic
    fun isAppointment(url: String): Boolean {
        return isLucaUrl(url) && url.contains("/appointment")
    }

    @JvmStatic
    fun isTestResult(url: String): Boolean {
        return isLucaUrl(url) && url.contains("/testresult/#")
    }
}
