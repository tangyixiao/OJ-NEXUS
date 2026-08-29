package com.ojnexus.judge

import com.ojnexus.core.model.JudgeId

/** Immutable process-wide registry. Duplicate judge registrations are programming errors. */
class JudgeRegistry(adapters: List<JudgeAdapter>) {

    private val adaptersByJudge: Map<JudgeId, JudgeAdapter> = adapters.associateBy { it.id }.also {
        require(it.size == adapters.size) { "duplicate judge adapter registration" }
    }

    fun adapter(judge: JudgeId): JudgeAdapter =
        adapterOrNull(judge) ?: error("judge adapter is not registered: ${judge.id}")

    fun adapterOrNull(judge: JudgeId): JudgeAdapter? = adaptersByJudge[judge]

    fun supportedJudges(): Set<JudgeId> = adaptersByJudge.keys
}
