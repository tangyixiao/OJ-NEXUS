package com.ojnexus.core.model

/**
 * Unified verdict across all judges. The raw judge-specific verdict is preserved by adapters;
 * everything downstream (timeline, analytics, review) only sees this enum.
 */
enum class Verdict(val isAccepted: Boolean) {
    AC(true),
    WA(false),
    TLE(false),
    MLE(false),
    RE(false),
    CE(false),
    PE(false),
    OTHER(false),
    ;

    companion object {

        /**
         * Maps a judge-specific raw verdict string to the unified verdict.
         * Null/blank/unknown input never throws — it degrades to [OTHER].
         */
        fun fromRaw(raw: String?): Verdict = when (raw?.trim()?.uppercase()) {
            "OK", "AC", "ACCEPTED", "CORRECT" -> AC
            "WA", "WRONG_ANSWER", "WRONG ANSWER" -> WA
            "TLE", "TIME_LIMIT_EXCEEDED", "TIME LIMIT EXCEEDED" -> TLE
            "MLE", "MEMORY_LIMIT_EXCEEDED", "MEMORY LIMIT EXCEEDED" -> MLE
            "RE", "RUNTIME_ERROR", "RUNTIME ERROR" -> RE
            "CE", "COMPILATION_ERROR", "COMPILATION ERROR" -> CE
            "PE", "PRESENTATION_ERROR", "PRESENTATION ERROR" -> PE
            else -> OTHER
        }
    }
}
