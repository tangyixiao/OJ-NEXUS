package com.ojnexus.feature.workspace

import com.ojnexus.R

internal fun workspaceActionLabelRes(state: WorkspaceState): Int = when {
    state.busy -> R.string.workspace_working
    state.mode == WorkspaceMode.SUBMIT -> R.string.workspace_mode_submit
    else -> R.string.workspace_mode_run
}
