package de.culture4life.luca.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.culture4life.luca.R

object ClipboardUtil {
    @JvmStatic
    @JvmOverloads
    fun copy(context: Context, label: String, content: String, successText: String = context.getString(R.string.clipboard_success)) {
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        if (clipboard == null) {
            Toast.makeText(context, R.string.clipboard_failed, Toast.LENGTH_SHORT).show()
        } else {
            val clip = ClipData.newPlainText(label, content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
        }
    }
}
