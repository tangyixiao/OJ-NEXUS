package com.ojnexus.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDaySourceTest {
    @Test
    fun `initial emission and refresh use the clock local day`() = runBlocking {
        val now = AtomicReference(Instant.parse("2026-09-06T23:59:00Z"))
        val source = SystemLocalDaySource(MutableClock(now, ZoneOffset.UTC))

        val days = mutableListOf<Long>()
        val job = launch { source.day.take(2).toList(days) }
        kotlinx.coroutines.yield()
        now.set(Instant.parse("2026-09-07T00:01:00Z"))
        source.refresh()
        job.join()
        val first = days.first()
        val second = days.last()

        assertEquals(Instant.parse("2026-09-06T00:00:00Z").epochSecond / 86_400, first)
        assertEquals(first + 1, second)
    }

    @Test
    fun `a resume signal refreshes a day after wall clock or timezone change`() = runBlocking {
        val now = AtomicReference(Instant.parse("2026-09-06T23:00:00Z"))
        val zone = AtomicReference<ZoneId>(ZoneOffset.UTC)
        val resumed = kotlinx.coroutines.channels.Channel<Unit>(capacity = 1)
        val source = SystemLocalDaySource(
            MutableClock(now) { zone.get() },
            resumeSignals = resumed.receiveAsFlow(),
        )
        val days = mutableListOf<Long>()
        val job = launch { withTimeout(2.seconds) { source.day.take(2).toList(days) } }
        kotlinx.coroutines.yield()
        now.set(Instant.parse("2026-09-07T00:01:00Z"))
        zone.set(ZoneOffset.ofHours(8))
        resumed.trySend(Unit)
        job.join()

        assertEquals(2, days.size)
        assertEquals(days.first() + 1, days.last())
    }

    private class MutableClock(
        private val instantRef: AtomicReference<Instant>,
        private val zoneProvider: () -> ZoneId,
    ) : Clock() {
        constructor(instantRef: AtomicReference<Instant>, zone: ZoneId) : this(instantRef, { zone })
        override fun getZone(): ZoneId = zoneProvider()
        override fun withZone(zone: ZoneId): Clock = MutableClock(instantRef) { zone }
        override fun instant(): Instant = instantRef.get()
    }
}
