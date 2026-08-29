package com.ojnexus.core.model

/** Explicit training-session lifecycle states. */
enum class SessionState {
    PLANNED,
    RUNNING,
    PAUSED,
    FINISHED,
    CANCELLED,
}

/** Session lifecycle events; see the SessionStateMachine in the domain layer. */
enum class SessionEvent {
    START,
    PAUSE,
    RESUME,
    FINISH,
    CANCEL,
}
