package com.ojnexus.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Shares plain text through the system sheet; a missing target must never crash the caller. */
object SessionDebriefShare {
    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "SESSION DEBRIEF"))
        } catch (_: ActivityNotFoundException) {
            // No share target is available; the clipboard copy already succeeded.
        }
    }
}
