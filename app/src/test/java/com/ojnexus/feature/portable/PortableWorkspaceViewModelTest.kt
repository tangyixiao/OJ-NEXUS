package com.ojnexus.feature.portable

import android.os.Looper
import com.ojnexus.core.portable.PortableApplyResult
import com.ojnexus.core.portable.PortableContractException
import com.ojnexus.core.portable.PortablePreview
import com.ojnexus.core.portable.PortableReject
import com.ojnexus.core.portable.PortableRejectCategory
import com.ojnexus.core.portable.PortableWorkspaceRepository
import com.ojnexus.core.portable.PortableWorkspaceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric contract tests for the portable workspace view model, mirroring
 * the house view-model test pattern: a fake repository, `runBlocking` bodies
 * and a bounded idle-loop that pumps the main looper until the observed state
 * settles. Covers preview/apply/cancel transport intents, the synthetic test
 * source derivation and the superseded-preview race.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortableWorkspaceViewModelTest {

    private val bytes = "package".toByteArray()

    private class FakeRepository(
        var previewBehavior: suspend (ByteArray) -> PortablePreview = { previewOf() },
        var applyBehavior: suspend (ByteArray) -> PortableApplyResult = { applyResultOf() },
        var exportBehavior: suspend () -> ByteArray = { ByteArray(0) },
    ) : PortableWorkspaceRepository {
        val repoState = MutableStateFlow(PortableWorkspaceState())
        override val state: StateFlow<PortableWorkspaceState> = repoState
        var applyCalls: Int = 0
        var cancelCalls: Int = 0
        var lastApplyBytes: ByteArray? = null

        /** False keeps the pre-set [repoState]; true mirrors the real PREVIEW state write. */
        var previewWritesState: Boolean = true

        override suspend fun preview(bytes: ByteArray): PortablePreview {
            val preview = previewBehavior(bytes)
            if (previewWritesState) {
                repoState.value = PortableWorkspaceState(
                    phase = PortableWorkspaceState.Phase.PREVIEW,
                    preview = preview,
                )
            }
            return preview
        }

        override suspend fun apply(bytes: ByteArray): PortableApplyResult {
            applyCalls += 1
            lastApplyBytes = bytes
            val result = applyBehavior(bytes)
            repoState.value = PortableWorkspaceState(
                phase = PortableWorkspaceState.Phase.APPLIED,
                applyResult = result,
            )
            return result
        }

        override suspend fun export(): ByteArray = exportBehavior()

        override fun cancel() {
            cancelCalls += 1
        }
    }

    @Test
    fun `preview success opens the dialog and flags a synthetic generator`() = runBlocking {
        val fake = FakeRepository(previewBehavior = { previewOf(generator = "synthetic-fixture-gen 1.0") })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        val state = awaitState(viewModel) { it.previewOpen }

        assertTrue(state.testSource)
        assertEquals("synthetic-fixture-gen 1.0", state.workspace.preview?.generator)
        assertNull(state.failure)
        assertNull(state.reject)
    }

    @Test
    fun `preview flags test source from the repository state tag`() = runBlocking {
        val fake = FakeRepository(previewBehavior = { previewOf(generator = "scope-export 1.0") })
        fake.previewWritesState = false
        fake.repoState.value = PortableWorkspaceState(message = "SYNTHETIC-TEST-FIXTURE")
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        val state = awaitState(viewModel) { it.previewOpen }

        assertTrue(state.testSource)
    }

    @Test
    fun `preview of a normal package does not flag test source`() = runBlocking {
        val fake = FakeRepository(previewBehavior = { previewOf(generator = "scope-export 1.0") })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        val state = awaitState(viewModel) { it.previewOpen }

        assertFalse(state.testSource)
    }

    @Test
    fun `contract rejection surfaces the reject and clears pending bytes`() = runBlocking {
        val reject = PortableReject(PortableRejectCategory.UNKNOWN_FIELD, "boom", "$.root")
        val fake = FakeRepository(previewBehavior = { throw PortableContractException(reject) })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        val state = awaitState(viewModel) { it.reject != null }

        assertFalse(state.previewOpen)
        assertEquals(PortableRejectCategory.UNKNOWN_FIELD, state.reject?.category)
        assertEquals("boom", state.reject?.detail)

        viewModel.apply()
        awaitState(viewModel) { fake.applyCalls == 0 && !it.previewOpen }
        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `unexpected preview failure surfaces the generic transport failure`() = runBlocking {
        val fake = FakeRepository(previewBehavior = { throw RuntimeException("disk") })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        val state = awaitState(viewModel) { it.failure == PortableTransferFailure.PREVIEW_FAILED }

        assertFalse(state.previewOpen)
        assertNull(state.reject)

        viewModel.apply()
        awaitState(viewModel) { fake.applyCalls == 0 }
        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `apply delegates to the repository with the pending bytes`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        awaitState(viewModel) { it.previewOpen }
        viewModel.apply()
        val state = awaitState(viewModel) {
            fake.applyCalls == 1 && it.workspace.phase == PortableWorkspaceState.Phase.APPLIED
        }

        assertTrue(bytes.contentEquals(fake.lastApplyBytes ?: ByteArray(-1)))
        assertFalse(state.previewOpen)
        assertEquals(1, state.workspace.applyResult?.rowsUpserted)
    }

    @Test
    fun `apply without a pending preview is a no-op`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.apply()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `cancel aborts an in-flight preview and the dialog never opens`() = runBlocking {
        val fake = FakeRepository(previewBehavior = { delay(60_000); previewOf() })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        viewModel.cancel()
        awaitState(viewModel) { fake.cancelCalls == 1 }
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.state.value
        assertFalse(state.previewOpen)
        assertNull(state.failure)
        assertNull(state.reject)
        assertEquals(1, fake.cancelCalls)
        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `cancel after a successful preview clears the offer without store writes`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        awaitState(viewModel) { it.previewOpen }
        viewModel.cancel()
        val state = awaitState(viewModel) { !it.previewOpen }

        assertFalse(state.previewOpen)
        assertFalse(state.testSource)
        assertNull(state.failure)
        assertNull(state.reject)
        assertEquals(1, fake.cancelCalls)
        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `a superseding read aborts the stale preview`() = runBlocking {
        var previews = 0
        val fake = FakeRepository(previewBehavior = { previews += 1; delay(60_000); previewOf() })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        viewModel.beginRead()
        val state = awaitState(viewModel) { it.transfer == PortableTransferPhase.READING }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(state.previewOpen)
        assertEquals(1, previews)
        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `beginRead clears a pending offer so apply becomes a no-op`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        awaitState(viewModel) { it.previewOpen }
        viewModel.beginRead()
        awaitState(viewModel) { it.transfer == PortableTransferPhase.READING }
        viewModel.apply()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, fake.applyCalls)
    }

    @Test
    fun `apply contract rejection clears pending bytes and closes the dialog`() = runBlocking {
        val reject = PortableReject(PortableRejectCategory.MISSING_REFERENCE, "gone", "$.attempts[0]")
        val fake = FakeRepository(applyBehavior = { throw PortableContractException(reject) })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        awaitState(viewModel) { it.previewOpen }
        viewModel.apply()
        val state = awaitState(viewModel) { it.reject != null }

        assertFalse(state.previewOpen)

        viewModel.apply()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.applyCalls)
    }

    @Test
    fun `rebuildExport returns bytes and enters the writing phase`() = runBlocking {
        val payload = "export".toByteArray()
        val fake = FakeRepository(exportBehavior = { payload })
        val viewModel = PortableWorkspaceViewModel(fake)

        val result = viewModel.rebuildExport()
        val state = awaitState(viewModel) { it.transfer == PortableTransferPhase.WRITING }

        assertTrue(payload.contentEquals(result ?: ByteArray(-1)))
        assertEquals(PortableTransferPhase.WRITING, state.transfer)
        assertNull(state.failure)
    }

    @Test
    fun `rebuildExport failure surfaces EXPORT_FAILED and returns to idle`() = runBlocking {
        val fake = FakeRepository(exportBehavior = { throw RuntimeException("corrupt") })
        val viewModel = PortableWorkspaceViewModel(fake)

        val result = viewModel.rebuildExport()
        val state = awaitState(viewModel) {
            it.transfer == PortableTransferPhase.IDLE &&
                it.failure == PortableTransferFailure.EXPORT_FAILED
        }

        assertNull(result)
        assertEquals(PortableTransferPhase.IDLE, state.transfer)
        assertEquals(PortableTransferFailure.EXPORT_FAILED, state.failure)
    }

    @Test
    fun `finishExport(true) reports the written document`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.finishExport(true)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.state.value
        assertTrue(state.exportWritten)
        assertEquals(PortableTransferPhase.IDLE, state.transfer)
        assertNull(state.failure)
    }

    @Test
    fun `finishExport(false) surfaces DOCUMENT_NOT_WRITTEN`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.finishExport(false)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.state.value
        assertFalse(state.exportWritten)
        assertEquals(PortableTransferFailure.DOCUMENT_NOT_WRITTEN, state.failure)
    }

    @Test
    fun `failRead surfaces the given transport failure and returns to idle`() = runBlocking {
        val fake = FakeRepository()
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.failRead(PortableTransferFailure.DOCUMENT_EMPTY)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.state.value
        assertEquals(PortableTransferPhase.IDLE, state.transfer)
        assertEquals(PortableTransferFailure.DOCUMENT_EMPTY, state.failure)
    }

    @Test
    fun `dismiss clears notices without touching the repository`() = runBlocking {
        val reject = PortableReject(PortableRejectCategory.UNKNOWN_FIELD, "boom", "$.root")
        val fake = FakeRepository(previewBehavior = { throw PortableContractException(reject) })
        val viewModel = PortableWorkspaceViewModel(fake)

        viewModel.preview(bytes)
        awaitState(viewModel) { it.reject != null }
        viewModel.dismiss()
        val state = awaitState(viewModel) { it.reject == null }

        assertNull(state.failure)
        assertEquals(0, fake.cancelCalls)
        assertEquals(0, fake.applyCalls)
    }

    private suspend fun awaitState(
        viewModel: PortableWorkspaceViewModel,
        predicate: (PortableWorkspaceUiState) -> Boolean,
    ): PortableWorkspaceUiState = withTimeout(2_000) {
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = viewModel.state.value
            if (predicate(state)) return@withTimeout state
            delay(1)
        }
        error("timed out waiting for portable workspace state")
    }

    private companion object {
        fun previewOf(generator: String = "synthetic-fixture-gen 1.0"): PortablePreview =
            PortablePreview(
                scopeId = "scope-1",
                sourcePlatform = "windows",
                generator = generator,
                createdAtEpochMs = 1_000L,
                counts = mapOf("problems" to 1),
                sources = emptyList(),
                decisions = emptyList(),
                conflicts = emptyList(),
                skipped = emptyList(),
                editableCount = 1,
                storedOnlyCount = 0,
                rejectedCount = 0,
                acceptAll = true,
                canonicalBytes = ByteArray(0),
                identity = "ab".repeat(32),
            )

        fun applyResultOf(): PortableApplyResult =
            PortableApplyResult(
                identity = "ab".repeat(32),
                editableCount = 1,
                storedOnlyCount = 0,
                rejectedCount = 0,
                rowsUpserted = 1,
                rowsDeleted = 0,
                identical = false,
            )
    }
}
