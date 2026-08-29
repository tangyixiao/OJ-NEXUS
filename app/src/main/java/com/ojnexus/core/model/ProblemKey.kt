package com.ojnexus.core.model

/**
 * Stable multi-OJ problem identity: judge + judge-side id. Two judges may use the same
 * external id string; the pair never collides. LOCAL problems use an app-side id.
 */
data class ProblemKey(
    val judge: JudgeId,
    val externalId: String,
) {
    override fun toString(): String = "${judge.id}/$externalId"
}
