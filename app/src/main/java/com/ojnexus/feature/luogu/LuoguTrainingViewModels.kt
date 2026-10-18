package com.ojnexus.feature.luogu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.judge.luogu.LuoguApiError
import com.ojnexus.judge.luogu.LuoguTrainingRepository
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingDetailDto
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Load state for the training catalog / detail screens: honest loading, empty, error. */
sealed class LuoguTrainingUiState<out T> {
    data object Loading : LuoguTrainingUiState<Nothing>()
    data object Empty : LuoguTrainingUiState<Nothing>()
    data class Ready<T>(val value: T) : LuoguTrainingUiState<T>()
    data class Failed(val retryable: Boolean, val notFound: Boolean = false) : LuoguTrainingUiState<Nothing>()
}

class LuoguTrainingCatalogViewModel(
    private val repository: LuoguTrainingRepository,
) : ViewModel() {

    private val state = MutableStateFlow<LuoguTrainingUiState<List<LuoguTrainingSummaryDto>>>(
        LuoguTrainingUiState.Loading,
    )
    val catalog: StateFlow<LuoguTrainingUiState<List<LuoguTrainingSummaryDto>>> = state

    init {
        refresh()
    }

    fun refresh() {
        state.value = LuoguTrainingUiState.Loading
        viewModelScope.launch {
            state.value = try {
                val items = repository.list()
                if (items.isEmpty()) LuoguTrainingUiState.Empty else LuoguTrainingUiState.Ready(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LuoguApiError) {
                LuoguTrainingUiState.Failed(retryable = true)
            }
        }
    }
}

class LuoguTrainingDetailViewModel(
    private val repository: LuoguTrainingRepository,
    private val trainingId: Long,
) : ViewModel() {

    private val state = MutableStateFlow<LuoguTrainingUiState<LuoguTrainingDetailDto>>(
        LuoguTrainingUiState.Loading,
    )
    val detail: StateFlow<LuoguTrainingUiState<LuoguTrainingDetailDto>> = state

    init {
        refresh()
    }

    fun refresh() {
        state.value = LuoguTrainingUiState.Loading
        viewModelScope.launch {
            state.value = try {
                LuoguTrainingUiState.Ready(repository.training(trainingId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LuoguApiError) {
                LuoguTrainingUiState.Failed(
                    retryable = e !is LuoguApiError.HttpError || e.statusCode >= 500,
                    notFound = e is LuoguApiError.HttpError && e.statusCode == 404,
                )
            }
        }
    }
}
