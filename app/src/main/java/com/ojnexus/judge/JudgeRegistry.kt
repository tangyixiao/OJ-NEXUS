package com.ojnexus.judge

import com.ojnexus.core.model.JudgeId

/** Immutable process-wide registry. Duplicate judge registrations are programming errors. */
class JudgeRegistry(
    adapters: List<JudgeAdapter>,
    accountConnectors: List<JudgeAccountConnector> = emptyList(),
) {

    private val adaptersByJudge: Map<JudgeId, JudgeAdapter> = adapters.associateBy { it.id }.also {
        require(it.size == adapters.size) { "duplicate judge adapter registration" }
    }
    private val connectorsByJudge = accountConnectors.associateBy { it.judgeId }.also {
        require(it.size == accountConnectors.size) { "duplicate judge account connector registration" }
        require(it.keys.all(adaptersByJudge::containsKey)) { "connector registered without adapter" }
    }
    private var coordinatorsByJudge: Map<JudgeId, JudgeSyncCoordinator> = emptyMap()

    fun adapter(judge: JudgeId): JudgeAdapter =
        adapterOrNull(judge) ?: error("judge adapter is not registered: ${judge.id}")

    fun adapterOrNull(judge: JudgeId): JudgeAdapter? = adaptersByJudge[judge]

    fun supportedJudges(): Set<JudgeId> = adaptersByJudge.keys

    fun accountConnector(judge: JudgeId): JudgeAccountConnector =
        connectorsByJudge[judge] ?: error("judge account connector is not registered: ${judge.id}")

    /** Manual-DI bootstrap hook; may be called once after repositories are constructed. */
    @Synchronized
    fun attachSyncCoordinators(coordinators: List<JudgeSyncCoordinator>) {
        require(coordinatorsByJudge.isEmpty()) { "sync coordinators already attached" }
        val mapped = coordinators.associateBy { it.judgeId }
        require(mapped.size == coordinators.size) { "duplicate judge sync coordinator registration" }
        require(mapped.keys.all(adaptersByJudge::containsKey)) { "coordinator registered without adapter" }
        coordinatorsByJudge = mapped
    }

    fun syncCoordinator(judge: JudgeId): JudgeSyncCoordinator =
        coordinatorsByJudge[judge] ?: error("judge sync coordinator is not registered: ${judge.id}")
}
