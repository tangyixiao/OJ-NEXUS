package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.LuoguProblemDetail
import com.ojnexus.judge.luogu.LuoguProblemDetailReader
import com.ojnexus.judge.luogu.LuoguProblemDetailSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LuoguProblemDetailUiState(
    val content: Loadable<LuoguProblemDetail> = Loadable.Loading,
    val source: LuoguProblemDetailSource? = null,
    val refreshing: Boolean = false,
    val refreshError: Boolean = false,
)

class LuoguProblemDetailViewModel(
    private val pid: String,
    private val repository: LuoguProblemDetailReader,
    testScope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope = testScope ?: viewModelScope
    private val mutableState = MutableStateFlow(LuoguProblemDetailUiState())
    val state: StateFlow<LuoguProblemDetailUiState> = mutableState.asStateFlow()

    init {
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = repository.fetch(pid)
                mutableState.value = LuoguProblemDetailUiState(
                    content = Loadable.Ready(result.detail),
                    source = result.source,
                    refreshError = result.source == LuoguProblemDetailSource.CACHE_FALLBACK,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = LuoguProblemDetailUiState(
                    content = Loadable.Failed(error.message.orEmpty()),
                )
            }
        }
    }

    fun refresh() {
        if (mutableState.value.refreshing) return
        mutableState.update { it.copy(refreshing = true, refreshError = false) }
        workScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = repository.refresh(pid)
                mutableState.update {
                    it.copy(
                        content = Loadable.Ready(result.detail),
                        source = result.source,
                        refreshing = false,
                        refreshError = result.source == LuoguProblemDetailSource.CACHE_FALLBACK,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(refreshing = false, refreshError = true) }
            }
        }
    }
}
