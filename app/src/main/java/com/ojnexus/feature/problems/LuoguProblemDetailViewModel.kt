package com.ojnexus.feature.problems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.LuoguProblemDetail
import com.ojnexus.judge.luogu.LuoguProblemDetailRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class LuoguProblemDetailViewModel(
    pid: String,
    repository: LuoguProblemDetailRepository,
) : ViewModel() {
    val state = flow<Loadable<LuoguProblemDetail>> {
        emit(Loadable.Loading)
        emit(Loadable.Ready(repository.fetch(pid)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}
