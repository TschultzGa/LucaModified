package de.culture4life.luca.util

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.getLaunchIntentForPackage(packageName: String, uri: Uri): Intent? =
    packageManager.getLaunchIntentForPackage(packageName)?.apply { data = uri }
