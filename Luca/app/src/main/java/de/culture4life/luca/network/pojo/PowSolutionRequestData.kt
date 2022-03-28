package de.culture4life.luca.network.pojo

import com.google.gson.annotations.SerializedName
import de.culture4life.luca.pow.PowChallenge

data class PowSolutionRequestData(

    @SerializedName("id")
    val id: String,

    @SerializedName("w")
    val w: String

) {

    constructor(powChallenge: PowChallenge) : this(powChallenge.id, powChallenge.w.toString(10))
}
