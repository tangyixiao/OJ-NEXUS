package com.ojnexus.feature.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.data.repository.ContestFocusRepository
import com.ojnexus.core.data.repository.ContestFocusSnapshot
import com.ojnexus.core.ui.Loadable
import java.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContestFocusViewModel(
    private val judge: String,
    private val contestId: String,
    private val repository: ContestFocusRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<Loadable<ContestFocusSnapshot>> = repository
        .observeFocus(judge, contestId)
        .map<ContestFocusSnapshot, Loadable<ContestFocusSnapshot>> { Loadable.Ready(it) }
        .catch { emit(Loadable.Failed(it.message ?: com.ojnexus.core.ui.localizedString(com.ojnexus.R.string.error_focus_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun cycleMarker(problemExternalId: String) {
        viewModelScope.launch {
            repository.cycleMarker(judge, contestId, problemExternalId)
        }
    }

    fun currentEpochSecond(): Long = clock.instant().epochSecond
}
