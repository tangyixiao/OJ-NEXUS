package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.DataResult
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.database.entity.RemoteProblemEntity
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.ProblemKey
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemStatus
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.judge.codeforces.CodeforcesUrls
import com.ojnexus.judge.atcoder.AtCoderUrls
import com.ojnexus.judge.luogu.LuoguUrls
import com.ojnexus.judge.luogu.LuoguPublicCatalogSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProblemsUiState(
    val totalCount: Int,
    val problems: List<Problem>,
    val allTags: List<String>,
    val filter: ProblemFilter,
    val sort: ProblemSort,
)

data class RemoteProblemsUiState(
    val judge: JudgeId = JudgeId.CODEFORCES,
    val query: String = "",
    val solvedFilter: Int = 0,
    val problems: List<RemoteProblemEntity> = emptyList(),
    val addedProblemIds: Map<String, Long> = emptyMap(),
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val catalogSyncing: Boolean = false,
    val catalogSyncItems: Int? = null,
    val catalogSyncError: String? = null,
)

class ProblemsViewModel(
    private val repository: ProblemRepository,
    private val demoSeeder: com.ojnexus.core.data.repository.DemoDataSeeder? = null,
    private val judgeDataRepository: JudgeDataRepository? = null,
    private val publicCatalogSync: LuoguPublicCatalogSync? = null,
) : ViewModel() {

    private val filter = MutableStateFlow(ProblemFilter())
    private val sort = MutableStateFlow(ProblemSort.UPDATED)

    val state: StateFlow<Loadable<ProblemsUiState>> =
        combine(
            repository.observeLibrary(),
            repository.observeTags(),
            filter,
            sort,
        ) { problems, tags, f, s ->
            Loadable.Ready(
                ProblemsUiState(
                    totalCount = problems.size,
                    problems = problems.applyFilterSort(f, s),
                    allTags = tags,
                    filter = f,
                    sort = s,
                ),
            )
        }
            .catch<Loadable<ProblemsUiState>> {
                emit(Loadable.Failed(it.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_load_failed)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val remoteCatalog = kotlinx.coroutines.flow.MutableStateFlow(RemoteProblemsUiState())
    val remoteState: StateFlow<RemoteProblemsUiState> = remoteCatalog
    private var remoteLoadJob: Job? = null
    private var catalogSyncJob: Job? = null

    fun enterRemoteCatalog() {
        if (remoteCatalog.value.problems.isEmpty() && !remoteCatalog.value.loading) reloadRemote()
    }

    fun setRemoteQuery(query: String) {
        remoteCatalog.update { it.copy(query = query) }
        reloadRemote()
    }

    fun setRemoteSolvedFilter(filter: Int) {
        remoteCatalog.update { it.copy(solvedFilter = filter) }
        reloadRemote()
    }

    fun setRemoteJudge(judge: JudgeId) {
        if (remoteCatalog.value.judge == judge) return
        catalogSyncJob?.cancel()
        remoteCatalog.update { it.copy(judge = judge, addedProblemIds = emptyMap()) }
        reloadRemote()
    }

    /** Explicit foreground import of Luogu's public catalog; no account is required. */
    fun syncLuoguCatalog() {
        val current = remoteCatalog.value
        if (current.judge != JudgeId.LUOGU || current.catalogSyncing || publicCatalogSync == null) return
        remoteCatalog.update {
            it.copy(
                catalogSyncing = true,
                catalogSyncItems = null,
                catalogSyncError = null,
            )
        }
        catalogSyncJob?.cancel()
        catalogSyncJob = viewModelScope.launch {
            try {
                val outcome = publicCatalogSync.syncPublicProblemCatalog(force = true)
                remoteCatalog.update {
                    it.copy(
                        catalogSyncing = false,
                        catalogSyncItems = outcome.itemsProcessed,
                        catalogSyncError = if (outcome.ok) {
                            null
                        } else {
                            outcome.errorMessage
                                ?: com.ojnexus.core.ui.localizedString(
                                    com.ojnexus.R.string.error_remote_catalog_unavailable,
                                )
                        },
                    )
                }
                if (outcome.ok) reloadRemote()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remoteCatalog.update {
                    it.copy(
                        catalogSyncing = false,
                        catalogSyncItems = null,
                        catalogSyncError = e.message
                            ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_remote_catalog_unavailable),
                    )
                }
            }
        }
    }

    fun loadMoreRemote() {
        val current = remoteCatalog.value
        if (!current.hasMore || current.loading) return
        loadRemotePage(append = true)
    }

    fun addRemoteToLibrary(remote: RemoteProblemEntity) {
        val remoteKey = "${remote.judge}:${remote.externalId}"
        if (remoteCatalog.value.addedProblemIds.containsKey(remoteKey)) return
        viewModelScope.launch {
            val result = repository.addProblem(
                ProblemRepository.ProblemInput(
                    key = ProblemKey(JudgeId.fromId(remote.judge) ?: return@launch, remote.externalId),
                    title = remote.name,
                    difficulty = remote.rating,
                    tags = remote.tags.split('\u001F').filter { it.isNotBlank() },
                    sourceUrl = remoteProblemUrl(remote),
                ),
            )
            val problemId = when (result) {
                is DataResult.Success -> result.value
                is DataResult.Failure -> repository.findProblemByKey(
                    ProblemKey(JudgeId.fromId(remote.judge) ?: return@launch, remote.externalId),
                )?.id
            }
            if (problemId != null) {
                remoteCatalog.update {
                    it.copy(addedProblemIds = it.addedProblemIds + (remoteKey to problemId), error = null)
                }
            } else if (result is DataResult.Failure) {
                remoteCatalog.update { it.copy(error = result.error.message) }
            }
        }
    }

    private fun reloadRemote() {
        remoteLoadJob?.cancel()
        remoteLoadJob = loadRemotePage(append = false)
    }

    private fun loadRemotePage(append: Boolean): Job = viewModelScope.launch {
        val current = remoteCatalog.value
        val pageOffset = if (append) current.offset else 0
        remoteCatalog.update {
            it.copy(
                loading = true,
                error = null,
                problems = if (append) it.problems else emptyList(),
                offset = if (append) it.offset else 0,
                hasMore = if (append) it.hasMore else false,
            )
        }
        delay(if (append) 0 else 250)
        try {
            val page = judgeDataRepository?.searchRemoteProblems(
                judge = remoteCatalog.value.judge,
                query = remoteCatalog.value.query,
                solvedFilter = remoteCatalog.value.solvedFilter,
                limit = REMOTE_PAGE_SIZE,
                offset = pageOffset,
            ).orEmpty()
            remoteCatalog.update {
                it.copy(
                    problems = if (append) it.problems + page else page,
                    offset = pageOffset + page.size,
                    hasMore = page.size == REMOTE_PAGE_SIZE,
                    loading = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            remoteCatalog.update {
                it.copy(
                    loading = false,
                    error = e.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_remote_catalog_unavailable),
                )
            }
        }
    }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun setStatus(status: ProblemStatus?) = filter.update { it.copy(status = status) }

    fun setJudge(judge: JudgeId?) = filter.update { it.copy(judge = judge) }

    fun setTag(tag: String?) = filter.update { it.copy(tag = tag) }

    fun toggleFavoriteOnly() = filter.update { it.copy(favoriteOnly = !it.favoriteOnly) }

    fun cycleSort() = sort.update { current ->
        ProblemSort.entries[(current.ordinal + 1) % ProblemSort.entries.size]
    }

    fun clearFilter() = filter.update { ProblemFilter() }

    fun toggleFavorite(problemId: Long, current: Boolean) {
        viewModelScope.launch { repository.setFavorite(problemId, !current) }
    }

    fun deleteProblem(problemId: Long) {
        viewModelScope.launch { repository.deleteProblem(problemId) }
    }

    /** Debug-only demo dataset entry points; no-ops when the seeder is absent (release). */
    fun insertDemoData() {
        viewModelScope.launch { demoSeeder?.insertDemoData() }
    }

    fun clearDemoData() {
        viewModelScope.launch { demoSeeder?.clearDemoData() }
    }

    private companion object {
        const val REMOTE_PAGE_SIZE = 50
    }
}

internal fun remoteProblemUrl(remote: RemoteProblemEntity): String? = when (JudgeId.fromId(remote.judge)) {
    JudgeId.CODEFORCES -> remote.contestId?.toLongOrNull()
        ?.let { CodeforcesUrls.problem(it, remote.index ?: "") }
    JudgeId.ATCODER -> remote.contestId?.let { AtCoderUrls.problem(it, remote.externalId) }
    JudgeId.LUOGU -> LuoguUrls.problem(remote.externalId)
    else -> null
}

internal fun remoteWorkspaceAvailable(remote: RemoteProblemEntity): Boolean =
    JudgeId.fromId(remote.judge) == JudgeId.LUOGU
