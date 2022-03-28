package de.culture4life.luca.ui.dialog

import android.content.DialogInterface
import androidx.annotation.StringRes

data class BaseDialogContent(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val positiveText: Int? = null,
    val positiveCallback: DialogInterface.OnClickListener? = null,
    @StringRes val neutralText: Int? = null,
    val neutralCallback: DialogInterface.OnClickListener? = null,
    @StringRes val negativeText: Int? = null,
    val negativeCallback: DialogInterface.OnClickListener? = null
)
