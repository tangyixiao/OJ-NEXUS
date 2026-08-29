package com.ojnexus.core.ui

/**
 * Minimal load-state wrapper for screen data. Screens map repository flows into this;
 * `Ready` carries the data and the UI renders section-level empty states from it.
 */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

inline fun <T, R> Loadable<T>.map(transform: (T) -> R): Loadable<R> = when (this) {
    is Loadable.Loading -> Loadable.Loading
    is Loadable.Ready -> Loadable.Ready(transform(value))
    is Loadable.Failed -> Loadable.Failed(message)
}
