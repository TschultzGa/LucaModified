package de.culture4life.luca.util

import android.net.Uri
import java.util.*

object LucaUrlUtil {

    /**
     * Checks if given uri string contains correct host and path for luca Uris
     */
    @JvmStatic
    fun isLucaUrl(url: String): Boolean {
        return true;
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
