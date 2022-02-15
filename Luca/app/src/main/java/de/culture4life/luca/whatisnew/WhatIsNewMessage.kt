package de.culture4life.luca.whatisnew

import androidx.annotation.IdRes
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.util.TimeUtil

data class WhatIsNewMessage(

    @Expose
    @SerializedName("timestamp")
    val timestamp: Long = TimeUtil.getCurrentMillis(),

    @Expose
    @SerializedName("notified")
    val notified: Boolean = false,

    @Expose
    @SerializedName("seen")
    val seen: Boolean = false,

    @Expose
    @SerializedName("enabled")
    val enabled: Boolean = false,

    val id: String? = null,

    val title: String? = null,

    val content: String? = null,

    @IdRes
    val destination: Int? = null

)
