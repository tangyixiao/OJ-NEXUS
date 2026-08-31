package com.ojnexus.feature.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojnexus.core.ui.Loadable
import com.ojnexus.judge.luogu.LuoguContestDetail
import com.ojnexus.judge.luogu.LuoguContestDetailRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class LuoguContestDetailViewModel(
    contestId: String,
    repository: LuoguContestDetailRepository,
) : ViewModel() {
    val state = flow<Loadable<LuoguContestDetail>> {
        emit(Loadable.Loading)
        emit(Loadable.Ready(repository.fetch(contestId)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}
