package de.culture4life.luca.whatisnew

import androidx.annotation.DrawableRes

data class WhatIsNewPage(
    val index: Int = -1,
    @DrawableRes val image: Int? = null,
    val heading: String,
    val description: String
)
