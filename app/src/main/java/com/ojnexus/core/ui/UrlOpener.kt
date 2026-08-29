package com.ojnexus.core.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens external pages in Chrome Custom Tabs with an external-browser fallback.
 * No WebView anywhere (product rule). Single entry point for problem/contest/profile URLs.
 */
object UrlOpener {

    fun open(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                // No browser available — nothing sensible to do; ignore rather than crash.
            }
        }
    }
}
