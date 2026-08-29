package com.ojnexus.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ojnexus.R

/**
 * Top-level destinations. Contest / Arena and other entry points are added through
 * dedicated navigation later — the bottom bar stays at five primary tabs.
 */
enum class NexusDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, R.drawable.ic_nav_dashboard),
    PROBLEMS("problems", R.string.nav_problems, R.drawable.ic_nav_problems),
    TRAINING("training", R.string.nav_training, R.drawable.ic_nav_training),
    ANALYTICS("analytics", R.string.nav_analytics, R.drawable.ic_nav_analytics),
    PROFILE("profile", R.string.nav_profile, R.drawable.ic_nav_profile),
}
