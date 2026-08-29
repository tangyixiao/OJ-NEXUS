package com.ojnexus.core.model

/**
 * Types of a training session. The session engine (timing, summaries) arrives in a later phase;
 * the enum is established now so the shell and screens reference one canonical definition.
 */
enum class TrainingType {
    PRACTICE,
    FOCUS,
    UPSOLVE,
    REVIEW,
}
