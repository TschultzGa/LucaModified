package de.culture4life.luca.util

import android.os.StrictMode

object StrictModeUtil {

    private val defaultThreadPolicy = StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()
        .penaltyLog()

    fun enableStrictMode() {
        StrictMode.setThreadPolicy(defaultThreadPolicy.build())
    }

    fun disableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    }

    fun permitDiskOperations() {
        val adjustedThreadPolicy = StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy())
            .permitDiskReads()
            .permitDiskWrites()
            .build()
        StrictMode.setThreadPolicy(adjustedThreadPolicy)
    }

    fun detectDiskOperations() {
        val adjustedThreadPolicy = StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy())
            .detectDiskReads()
            .detectDiskWrites()
            .build()
        StrictMode.setThreadPolicy(adjustedThreadPolicy)
    }

}