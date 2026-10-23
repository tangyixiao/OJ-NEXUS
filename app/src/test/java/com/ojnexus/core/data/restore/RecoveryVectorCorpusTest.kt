package com.ojnexus.core.data.restore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertTrue(vectors.getValue("RV013").preconditions.any { it.contains("NEW_DATA") && it.contains("valid") })
        assertTrue(vectors.getValue("RV018").preconditions.any { it.contains("OLD_DATA") && it.contains("OLD_GEN") && it.contains("valid") })
        assertTrue(vectors.getValue("RV017").preconditions.any { it.contains("rollback source", ignoreCase = true) && it.contains("target is unavailable", ignoreCase = true) })
        assertTrue(vectors.getValue("RV026").preconditions.any { it.contains("OLD_DATA") && it.contains("transaction") && it.contains("rollback") })
        val unknownGenerationWork = vectors.getValue("RV022")
        assertNull(unknownGenerationWork.android?.expected_generation)
        assertEquals("g-current", unknownGenerationWork.android?.current_generation)
        assertEquals(RestoreWorkDecision.LEGACY, unknownGenerationWork.android?.expected_decision)
        assertTrue(unknownGenerationWork.preconditions.single().let {
            it.contains("NEW_DATA") && it.contains("NEW_GEN") &&
                it.contains("no captured expected_generation") && it.contains("no write authorization")
        })
        assertEquals("Work is reconciled and requeued with captured expected_generation NEW_GEN", unknownGenerationWork.retry.after)
        vectors.values.filter { !it.retry.allowed }.forEach { assertEquals("NONE", it.retry.after) }
    }

    @Test
    fun `nullable contract keys are required and recovery journal may be explicitly null`() {
        val valid = RecoveryVectorFixtures.loadTextForTest()
        val nullJournal = mutateVector(valid, "RV010") { vector ->
            val android = vector.getValue("android").jsonObject.toMutableMap()
            android["journal"] = JsonNull
            vector["android"] = JsonObject(android)
        }
        assertNull(RecoveryVectorFixtures.decode(nullJournal).vectors.single { it.id == "RV010" }.android?.journal)
        val legacyProjection = RecoveryVectorFixtures.load().vectors.single { it.id == "RV022" }.android
        assertNull(legacyProjection?.expected_generation)
        assertEquals(RestoreWorkDecision.LEGACY, legacyProjection?.expected_decision)

        assertRejected(mutateVector(valid, "RV010") { it.remove("android") })
        assertRejected(mutateVector(valid, "RV002") { vector ->
            val expected = vector.getValue("expected").jsonObject.toMutableMap()
            expected.remove("error")
            vector["expected"] = JsonObject(expected)
        })
        assertRejected(mutateVector(valid, "RV010") { vector ->
            val android = vector.getValue("android").jsonObject.toMutableMap()
            android.remove("journal")
            vector["android"] = JsonObject(android)
        })
        assertRejected(mutateVector(valid, "RV020") { vector ->
            val android = vector.getValue("android").jsonObject.toMutableMap()
            android.remove("expected_generation")
            vector["android"] = JsonObject(android)
        })
        assertRejected(mutateVector(valid, "RV010") { vector ->
            val android = vector.getValue("android").jsonObject.toMutableMap()
            android["expected_generation"] = JsonNull
            vector["android"] = JsonObject(android)
        })
        assertRejected(mutateVector(valid, "RV020") { vector ->
            val android = vector.getValue("android").jsonObject.toMutableMap()
            android["journal"] = JsonNull
            vector["android"] = JsonObject(android)
        })
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
        assertRejected(valid.replaceFirst("\"after\": \"Corrected complete input\"", "\"after\": \"RETRY\""))
    }

    private fun assertRejected(text: String) {
        try {
            RecoveryVectorFixtures.decode(text)
            throw AssertionError("invalid recovery corpus was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    private fun mutateVector(text: String, id: String, mutation: (MutableMap<String, JsonElement>) -> Unit): String {
        val root = Json.parseToJsonElement(text).jsonObject
        val updatedVectors = root.getValue("vectors").jsonArray.map { element ->
            val vector = element.jsonObject
            if (vector.getValue("id").jsonPrimitive.content != id) return@map element
            val updated = vector.toMutableMap()
            mutation(updated)
            JsonObject(updated)
        }
        return JsonObject(root.toMutableMap().apply { put("vectors", JsonArray(updatedVectors)) }).toString()
    }
}
