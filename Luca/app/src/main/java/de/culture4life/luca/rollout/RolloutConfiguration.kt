package de.culture4life.luca.rollout

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.culture4life.luca.network.pojo.RolloutRatioResponseData
import kotlin.math.abs

data class RolloutConfiguration(

    @Expose
    @SerializedName("id")
    val id: String,

    @Expose
    @SerializedName("rolloutRatio")
    val rolloutRatio: Float = MINIMUM_ROLLOUT_RATIO,

    @Expose
    @SerializedName("gambledRatio")
    val gambledRatio: Float = gambleRatio()
) {

    constructor(responseData: RolloutRatioResponseData) : this(responseData.id, responseData.rolloutRatio)

    val rolloutToThisDevice: Boolean
        get() = rolloutCompleted || (rolloutStarted && rolloutRatio > gambledRatio)

    val rolloutStarted: Boolean
        get() = rolloutRatio > MINIMUM_ROLLOUT_RATIO

    val rolloutCompleted: Boolean
        get() = rolloutRatio == MAXIMUM_ROLLOUT_RATIO

    /**
     * Rollout percentage changes smaller than 0.000001% will not be recognized.
     * This is used to not treat deltas caused by rounding as changes in rollout percentage.
     */
    fun rolloutRatioEquals(ratio: Float) = abs(rolloutRatio - ratio) < 0.000001

    companion object {
        const val MINIMUM_ROLLOUT_RATIO = 0F
        const val MAXIMUM_ROLLOUT_RATIO = 1F
        fun gambleRatio() = Math.random().toFloat()
    }
}
