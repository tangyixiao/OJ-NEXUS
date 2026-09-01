package com.ojnexus.judge.luogu.open

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoguOpenResultWorkerTest {
    @Test
    fun `request spec carries only the trimmed request id`() {
        val spec = requireNotNull(LuoguResultWorkRequestFactory.spec("  req-42  "))

        assertEquals("req-42", spec.requestId)
        assertEquals("luogu-result:req-42", spec.uniqueWorkName)
        assertEquals(setOf(LuoguResultWorkRequestFactory.REQUEST_ID_KEY), spec.inputData.keys)
        assertEquals("req-42", spec.inputData[LuoguResultWorkRequestFactory.REQUEST_ID_KEY])
        assertTrue(spec.requiresConnectedNetwork)
        assertEquals(10_000L, spec.initialDelayMillis)
        assertEquals(30_000L, spec.backoffDelayMillis)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(ExistingWorkPolicy.KEEP, spec.existingWorkPolicy)
    }

    @Test
    fun `blank request id produces no background work spec`() {
        assertEquals(null, LuoguResultWorkRequestFactory.spec("  "))
    }

    @Test
    fun `worker decisions map to WorkManager results`() {
        assertEquals(
            ListenableWorker.Result.Success::class.java,
            LuoguResultWorkDecision.Success.toWorkerResult().javaClass,
        )
        assertEquals(
            ListenableWorker.Result.Retry::class.java,
            LuoguResultWorkDecision.Retry.toWorkerResult().javaClass,
        )
        assertEquals(
            ListenableWorker.Result.Failure::class.java,
            LuoguResultWorkDecision.Failure.toWorkerResult().javaClass,
        )
    }

    @Test
    fun `request builder applies the safe WorkManager constraints`() {
        val spec = requireNotNull(LuoguResultWorkRequestFactory.spec("req-42"))
        val request = LuoguResultWorkRequestFactory.request(spec)
        val workSpec = request.workSpec

        assertEquals(NetworkType.CONNECTED, workSpec.constraints.requiredNetworkType)
        assertEquals(10_000L, workSpec.initialDelay)
        assertEquals(30_000L, workSpec.backoffDelayDuration)
        assertEquals(BackoffPolicy.EXPONENTIAL, workSpec.backoffPolicy)
        assertEquals("req-42", workSpec.input.getString(LuoguResultWorkRequestFactory.REQUEST_ID_KEY))
        assertEquals(1, workSpec.input.size())
    }
}
