package com.ojnexus.core.model

/**
 * Identifier for an online judge. Display names are fixed proper nouns (not translatable UI
 * copy) and rendered in the app's uppercase telemetry style.
 *
 * [LOCAL] marks problems created manually inside the app; its identity never syncs.
 *
 * Adapters own all judge-specific behavior; core code only ever refers to judges through this
 * enum so a broken adapter can never leak into another judge's data path.
 */
enum class JudgeId(
    val id: String,
    val displayName: String,
) {
    LOCAL("local", "LOCAL"),
    CODEFORCES("codeforces", "CODEFORCES"),
    ATCODER("atcoder", "ATCODER"),
    LUOGU("luogu", "LUOGU"),
    ;

    companion object {
        fun fromId(id: String): JudgeId? = entries.firstOrNull { it.id == id }
    }
}
