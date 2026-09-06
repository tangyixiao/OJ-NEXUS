package com.ojnexus.core.time

import java.time.Clock
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

interface LocalDaySource {
    val day: Flow<Long>

    fun refresh()
}

/** Emits the local epoch day immediately, at the next midnight, and after a resume signal. */
class SystemLocalDaySource(
    private val clock: Clock,
    private val resumeSignals: Flow<Unit> = emptyFlow(),
) : LocalDaySource {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val day: Flow<Long> = flow {
        val wakeups = merge(refreshes, resumeSignals)
        while (currentCoroutineContext().isActive) {
            val currentDay = clock.instant().atZone(clock.zone).toLocalDate().toEpochDay()
            emit(currentDay)
            val nextMidnight = LocalDate.ofEpochDay(currentDay + 1)
                .atStartOfDay(clock.zone)
                .toInstant()
                .toEpochMilli()
            val waitMillis = max(1L, nextMidnight - clock.millis())
            withTimeoutOrNull(waitMillis) { wakeups.first() }
        }
    }

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }
}
