package com.ojnexus.feature.settings

internal fun shouldScrollToFocusedSettingsSection(
    focusOpenApp: Boolean,
    focusLuogu: Boolean,
    viewportTop: Int?,
    targetRootY: Int?,
): Boolean =
    (focusOpenApp || focusLuogu) && viewportTop != null && targetRootY != null
