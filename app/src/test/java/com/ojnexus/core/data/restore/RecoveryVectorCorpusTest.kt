package com.ojnexus.core.data.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryVectorCorpusTest {
    @Test
    fun `canonical corpus has all stable IDs and only the nine declared projections`() {
        val corpus = RecoveryVectorFixtures.load()
        assertEquals((1..32).map { "RV%03d".format(it) }, corpus.vectors.map { it.id })
        assertEquals(
            setOf("RV010", "RV011", "RV012", "RV013", "RV014", "RV016", "RV020", "RV021", "RV022"),
            corpus.vectors.filter { it.android != null }.map { it.id }.toSet(),
        )
    }

    @Test
    fun `relations keep blocked aborted empty and retry outcomes honest`() {
        val vectors = RecoveryVectorFixtures.load().vectors.associateBy { it.id }
        assertEquals(
            setOf("RV021"),
            vectors.values.filter { it.expected.originating_work_may_write }.map { it.id }.toSet(),
        )
        vectors.values.filter { it.expected.phase == VectorPhase.BLOCKED }.forEach { assertTrue(it.expected.preserve_artifacts) }
        vectors.values.filter { it.expected.phase == VectorPhase.ABORTED && it.operation == VectorOperation.IMPORT }.forEach {
            assertEquals(ActiveData.OLD, it.expected.active_data)
            assertEquals(GenerationOutcome.OLD, it.expected.generation)
        }
        assertEquals(ActiveData.EMPTY, vectors.getValue("RV029").expected.active_data)
        assertEquals(ActiveData.OLD, vectors.getValue("RV030").expected.active_data)
        assertEquals(WriteSemantics.REPLACE, vectors.getValue("RV029").write_semantics)
        assertEquals(WriteSemantics.UPSERT, vectors.getValue("RV030").write_semantics)
        assertTrue(vectors.getValue("RV010").preconditions.any { it.contains("confirmation") && it.contains("durably recorded") })
        vectors.values.filter { !it.retry.allowed }.forEach { assertEquals("NONE", it.retry.after) }
    }

    @Test
    fun `decoder rejects unsupported version duplicate ID absent fields and unknown fields`() {
        val valid = RecoveryVectorFixtures.loadTextForTest()
        assertRejected(valid.replace("\"format_version\": 1", "\"format_version\": 2"))
        assertRejected(valid.replaceFirst("\"RV002\"", "\"RV001\""))
        assertRejected(valid.replaceFirst("\"expected\":", "\"missing\":"))
        assertRejected(valid.replaceFirst("\"vectors\":", "\"unexpected\": true, \"vectors\":"))
    }

    @Test
    fun `decoder rejects unknown enum and broken safety relations`() {
        val valid = RecoveryVectorFixtures.loadTextForTest()
        assertRejected(valid.replaceFirst("\"operation\": \"IMPORT\"", "\"operation\": \"PURGE\""))
        val oldWorkerStart = valid.indexOf("\"id\": \"RV020\"")
        val beforeOldWorker = valid.substring(0, oldWorkerStart)
        val oldWorkerVector = valid.substring(oldWorkerStart)
        assertRejected(beforeOldWorker + oldWorkerVector.replaceFirst("\"originating_work_may_write\": false", "\"originating_work_may_write\": true"))
        val blockedVectorStart = valid.indexOf("\"id\": \"RV017\"")
        val beforeBlocked = valid.substring(0, blockedVectorStart)
        val blockedVector = valid.substring(blockedVectorStart)
        assertRejected(beforeBlocked + blockedVector.replaceFirst("\"preserve_artifacts\": true", "\"preserve_artifacts\": false"))
        assertRejected(valid.replaceFirst("\"active_data\": \"EMPTY\"", "\"active_data\": \"OLD\""))
        assertRejected(Regex("\"allowed\": false,\\s+\"after\": \"NONE\"").replaceFirst(valid, "\"allowed\": true, \"after\": \"NONE\""))
    }

    private fun assertRejected(text: String) {
        try {
            RecoveryVectorFixtures.decode(text)
            throw AssertionError("invalid recovery corpus was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
