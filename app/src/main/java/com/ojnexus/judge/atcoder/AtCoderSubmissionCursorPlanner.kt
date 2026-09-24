package com.ojnexus.judge.atcoder

import com.ojnexus.judge.atcoder.api.dto.AtCoderSubmissionDto

data class AtCoderCursorDecision(
    val shouldContinue: Boolean,
    val stalled: Boolean,
    val nextFromSecond: Long?,
    val durableCursorSecond: Long?,
    val newIds: Set<Long>,
)

/** Pure progress invariant for AtCoder Problems timestamp pagination. */
object AtCoderSubmissionCursorPlanner {
    fun plan(
        fromSecond: Long,
        page: List<AtCoderSubmissionDto>,
        pageSize: Int,
        seenIds: Set<Long>,
    ): AtCoderCursorDecision {
        require(pageSize > 0)
        val maxSecond = page.maxOfOrNull { it.epochSecond }
        val newIds = page.asSequence().map { it.id }.filterNot(seenIds::contains).toSet()
        if (page.size < pageSize) {
            return AtCoderCursorDecision(false, false, null, maxSecond, newIds)
        }
        val cursorAdvanced = maxSecond != null && maxSecond > fromSecond
        val progressed = cursorAdvanced || newIds.isNotEmpty()
        if (!progressed || maxSecond == null) {
            return AtCoderCursorDecision(false, true, null, maxSecond ?: fromSecond, newIds)
        }
        return AtCoderCursorDecision(true, false, maxSecond, maxSecond, newIds)
    }
}
