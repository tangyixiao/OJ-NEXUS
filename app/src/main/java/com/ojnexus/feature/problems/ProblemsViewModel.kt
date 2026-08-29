package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.model.Problem
import com.ojnexus.core.model.ProblemStatus
import com.ojnexus.core.ui.Loadable
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

class ProblemsViewModel(
    private val repository: ProblemRepository,
    private val demoSeeder: com.ojnexus.core.data.repository.DemoDataSeeder? = null,
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
            .catch<Loadable<ProblemsUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

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
}
