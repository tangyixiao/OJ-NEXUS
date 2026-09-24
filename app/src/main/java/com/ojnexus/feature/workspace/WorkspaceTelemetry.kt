package com.ojnexus.feature.workspace

data class WorkspaceTelemetry(
    val mode: WorkspaceMode,
    val language: String,
    val codeLines: Int,
    val draftState: WorkspaceDraftState,
)

fun workspaceTelemetry(state: WorkspaceState): WorkspaceTelemetry = WorkspaceTelemetry(
    mode = state.mode,
    language = state.language,
    codeLines = sourceLineCount(state.code),
    draftState = state.draftState,
)

fun sourceLineCount(code: String): Int {
    if (code.isBlank()) return 0
    val normalized = code
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd('\n')
    return normalized.count { it == '\n' } + 1
}
