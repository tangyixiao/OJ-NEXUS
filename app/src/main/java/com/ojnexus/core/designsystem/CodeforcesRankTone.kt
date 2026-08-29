package com.ojnexus.core.designsystem

/**
 * Codeforces rank colors — a deliberate, documented exception to the single-accent rule.
 * Used ONLY for small-area identity marks (rank tags), never for large surfaces, and the
 * rank text is always rendered alongside the color (accessibility rule: color is never
 * the sole signal). Unknown ranks fall back to Neutral gray.
 */
enum class CodeforcesRankTone(val colorHex: Long) {
    NEWBIE(0xFF9AA3AC),
    PUPIL(0xFF34C979),
    SPECIALIST(0xFF2BC8D4),
    EXPERT(0xFF4F7DFF),
    CANDIDATE_MASTER(0xFFB36DFF),
    MASTER(0xFFFFB24D),
    INTERNATIONAL_MASTER(0xFFFFA14D),
    GRANDMASTER(0xFFFF4F5E),
    INTERNATIONAL_GRANDMASTER(0xFFFF3B47),
    LEGENDARY_GRANDMASTER(0xFFFF2D2D),
    NEUTRAL(0xFF9AA3AC),
    ;

    companion object {

        /** Matches Codeforces rank names case-insensitively; longest prefix wins so
         *  "international grandmaster" never resolves to plain GRANDMASTER. */
        fun of(rank: String?): CodeforcesRankTone {
            val normalized = rank?.trim()?.lowercase() ?: return NEUTRAL
            return entries
                .filter { it != NEUTRAL }
                .sortedByDescending { it.name.length }
                .firstOrNull { candidate ->
                    val words = candidate.name.lowercase().replace('_', ' ')
                    normalized.contains(words)
                } ?: NEUTRAL
        }
    }
}
