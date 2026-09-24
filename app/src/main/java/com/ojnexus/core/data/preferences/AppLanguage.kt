package com.ojnexus.core.data.preferences

enum class AppLanguage(val localeTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    companion object {
        fun fromLocaleTags(localeTags: String): AppLanguage = when (localeTags) {
            ENGLISH.localeTag -> ENGLISH
            SIMPLIFIED_CHINESE.localeTag -> SIMPLIFIED_CHINESE
            else -> SYSTEM
        }
    }
}
