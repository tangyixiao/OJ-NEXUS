package com.ojnexus.app

import com.ojnexus.AppContainer
import com.ojnexus.core.data.restore.RecoveryDisposition
import com.ojnexus.core.data.restore.RestoreFailure

sealed interface NexusStartupState {
    data object Starting : NexusStartupState
    data class Ready(val container: AppContainer) : NexusStartupState
    data class Blocked(val failure: RestoreFailure, val recovery: RecoveryDisposition) : NexusStartupState
}

interface NexusStartupStateOwner {
    val startupState: NexusStartupState
}
