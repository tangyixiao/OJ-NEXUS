package com.ojnexus.core.startup

/**
 * Startup tracing harness for the cold-start budget (Phase 105). Pure and injectable: the
 * application begins/ends named spans, tests inject a controllable clock, and the recorded
 * chain answers "how far from budget are we" without any androidx.tracing dependency.
 */
data class StartupSpan(
    val name: String,
    val startedAtUptimeMs: Long,
    val endedAtUptimeMs: Long? = null,
) {
    /** null while the span is still open; a negative duration clamps to 0. */
    fun durationMs(): Long? = endedAtUptimeMs?.let { end -> (end - startedAtUptimeMs).coerceAtLeast(0) }
}

class StartupTracer(private val clock: () -> Long = System::currentTimeMillis) {

    private val recorded = linkedMapOf<String, StartupSpan>()

    /** Opens (or re-opens) a named span at the current clock reading. */
    fun begin(name: String) {
        recorded[name] = StartupSpan(name = name, startedAtUptimeMs = clock())
    }

    /** Closes a span; ending an unknown span is a no-op, re-ending keeps the FIRST end time. */
    fun end(name: String) {
        val existing = recorded[name] ?: return
        if (existing.endedAtUptimeMs != null) return
        recorded[name] = existing.copy(endedAtUptimeMs = clock())
    }

    val spans: List<StartupSpan> get() = recorded.values.toList()

    /** Wall distance from the earliest begin to the latest end; null when nothing is closed. */
    fun totalMs(): Long? {
        val closed = spans.mapNotNull { it.durationMs() }
        if (closed.isEmpty()) return null
        val firstBegin = spans.minOf { it.startedAtUptimeMs }
        val lastEnd = spans.maxOf { it.endedAtUptimeMs ?: it.startedAtUptimeMs }
        return (lastEnd - firstBegin).coerceAtLeast(0)
    }
}

object StartupBudget {
    /** Cold start to interactive Dashboard budget, milliseconds (Phase 105 DoD). */
    const val COLD_START_BUDGET_MS = 1500L

    val WITHIN: String = "WITHIN_BUDGET"
    val OVER: String = "OVER_BUDGET"

    /** Pure budget verdict; open (null) totals never claim success. */
    fun verdict(totalMs: Long?): String =
        if (totalMs != null && totalMs <= COLD_START_BUDGET_MS) WITHIN else OVER
}
