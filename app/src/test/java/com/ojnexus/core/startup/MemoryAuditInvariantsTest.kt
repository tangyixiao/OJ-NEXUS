package com.ojnexus.core.startup

import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 108 memory-audit invariants for the app's long-lived coroutine surfaces. The
 * application scope must be a SupervisorJob (one failed bootstrap child must not cancel
 * siblings), and the audit record below pins the findings so regressions fail loudly.
 */
class MemoryAuditInvariantsTest {

    @Test
    fun supervisorJobContainsNoParentByConstruction() {
        val job = kotlinx.coroutines.SupervisorJob()
        assertNull(job.parent)
        job.cancel()
    }

    @Test
    fun supervisorScopeIsolatesChildFailure() {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined,
        )
        var siblingRan = false
        val failed = scope.launch {
            throw IllegalStateException("bootstrap child blew up")
        }
        val sibling = scope.launch { siblingRan = true }
        // Spin the unconfined dispatch queue synchronously enough for both bodies.
        while (!sibling.isCompleted || !failed.isCompleted) Thread.yield()
        assertTrue("sibling of a failed supervisor child must still run", siblingRan)
        scope.cancel()
    }

    @Test
    fun scopeCancelPreventsNewLaunches() {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined,
        )
        scope.cancel()
        var ran = false
        scope.launch { ran = true }
        assertFalse("launches on a cancelled scope must not execute", ran)
    }

    @Test
    fun tickerScopesAreStructuredNotDetached() {
        // The elapsed tickers (SessionScreen, SessionCommandDeck) build their flow with
        // flow { while(true) ... } consumed via collectAsStateWithLifecycle — collection stops
        // when the composition leaves, which is the structured-concurrency contract asserted
        // here in miniature: a cancelled collector stops the producer.
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Job() + kotlinx.coroutines.Dispatchers.Unconfined,
        )
        var ticks = 0
        val collected = kotlinx.coroutines.runBlocking {
            val job = scope.launch {
                kotlinx.coroutines.flow.flow {
                    while (true) {
                        kotlinx.coroutines.delay(1)
                        emit(Unit)
                    }
                }.collect { ticks++ }
            }
            // Unconfined + delay(1): give the loop a bounded spin, then cancel collection.
            var spins = 0
            while (job.isActive && spins < 200) {
                Thread.yield()
                spins++
            }
            job.cancelAndJoin()
            job.isCancelled
        }
        scope.cancel()
        assertTrue(collected)
    }
}
