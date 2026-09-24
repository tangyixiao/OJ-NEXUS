package com.ojnexus.core.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/** Reads a UI fallback through the currently selected per-app locale. */
fun localizedString(@StringRes resourceId: Int): String {
    val application = GlobalContext.application
    val languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (languageTags.isBlank()) return application.getString(resourceId)

    val configuration = Configuration(application.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(languageTags))
    }
    return application.createConfigurationContext(configuration).getString(resourceId)
}
