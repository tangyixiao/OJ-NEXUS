package com.ojnexus.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.entity.ProblemTagCrossRef
import com.ojnexus.core.database.entity.ProblemTagEntity
import com.ojnexus.core.database.entity.ReviewEntity
import com.ojnexus.core.model.ProblemStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 137 contract-vector parity: every golden vector in
 * `contracts/problems/v1/golden/` is replayed through the REAL Room SQL path
 * ([ProblemDao.findLibraryPage] — the same [LibraryQueryBuilder] query the UI's
 * Pager runs). The vector's `expect` list freezes the phase-136 semantics:
 * identity filter/order for search, status derivation, tag exactness,
 * difficulty bounds, and the stable code-point tie-break.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProblemQueryVectorTest {

    private lateinit var database: OjNexusDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OjNexusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** The contracts dir is a test-classpath resource (build.gradle adds it); the
     * resource root is `contracts/`, so vectors live under `problems/v1/golden`.
     */
    private fun goldenFiles(): List<File> {
        val url = javaClass.classLoader!!.getResource("problems/v1/golden")
            ?: error("golden vectors not on the test classpath (expected contracts/ as a test resource)")
        val dir = File(url.toURI())
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "cases.json" }
            ?.sortedBy { it.name }.orEmpty()
        return files
    }

    @Test
    fun allContractVectorsReproduceThroughTheRoomPath() = runBlocking {
        val dao = database.problemDao()
        val files = goldenFiles()
        assertEquals("expected 28 golden vectors", 28, files.size)
        val failures = mutableListOf<String>()
        for (file in files.sortedBy { it.name }) {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            database.clearAllTables()
            seed(root["dataset"]!!.jsonArray)
            val query = vectorQuery(root["query"]!!.jsonObject)
            val actual = dao.findLibraryPage(query)
                .map { "${it.problem.judge}/${it.problem.externalId}" }
            val expected = root["expect"]!!.jsonArray.map { it.jsonPrimitive.content }
            if (actual != expected) {
                failures.add("${root["name"]!!.jsonPrimitive.content}: expected $expected but Room returned $actual")
            }
        }
        assertEquals("contract-vector parity failures:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    /** Review P2-1 pin: an ideographic space (U+3000, realistic from CJK IMEs) is NOT
     * in the contract's ASCII trim set — the query must match nothing, not "tree".
     */
    @Test
    fun ideographicSpaceQueryIsNotTrimmed() = runBlocking {
        seedU3000Fixture()
        val query = LibraryQuery(search = "\u3000tree")
        assertEquals(emptyList<String>(), database.problemDao().findLibraryPage(query)
            .map { "${it.problem.judge}/${it.problem.externalId}" })
    }

    /** Review P2-1 pin: a query of only a non-ASCII whitespace char stays a live
     * clause with its two bind args (no bind-count mismatch) and matches nothing.
     */
    @Test
    fun nonAsciiWhitespaceOnlyQueryKeepsClauseAndArgsMatched() = runBlocking {
        seedU3000Fixture()
        val query = LibraryQuery(search = "\u2000")
        assertEquals(
            emptyList<String>(),
            database.problemDao().findLibraryPage(query)
                .map { "${it.problem.judge}/${it.problem.externalId}" },
        )
        assertEquals(
            "clause stays live with matched bind args; no bind-count error, zero matches",
            0,
            database.problemDao().observeMatchCount(query).first().toInt(),
        )
    }

    private suspend fun seedU3000Fixture() {
        val dao = database.problemDao()
        dao.insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = "u3000-A",
                title = "Two Doors",
                titleLower = "two doors",
                difficulty = 800,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        dao.insert(
            ProblemEntity(
                judge = "luogu",
                externalId = "u3000-B",
                title = "园林",
                titleLower = "园林",
                difficulty = 900,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
    }

    private suspend fun seed(dataset: kotlinx.serialization.json.JsonArray) {
        val dao = database.problemDao()
        for (element in dataset) {
            val row = element.jsonObject
            fun str(name: String): String = row[name]!!.jsonPrimitive.content
            val problemId = dao.insert(
                ProblemEntity(
                    judge = str("judge"),
                    externalId = str("externalId"),
                    title = str("title"),
                    titleLower = str("title").lowercase(),
                    difficulty = row["difficulty"]?.jsonPrimitive?.intOrNull,
                    createdAt = str("createdAt").toLong(),
                    updatedAt = str("updatedAt").toLong(),
                    attemptCount = row["attemptCount"]?.jsonPrimitive?.intOrNull ?: 0,
                    solved = row["solved"]?.jsonPrimitive?.booleanOrNull ?: false,
                    favorite = row["favorite"]?.jsonPrimitive?.booleanOrNull ?: false,
                ),
            )
            for (tag in row["tags"]?.jsonArray ?: emptyList()) {
                val name = tag.jsonPrimitive.content
                val tagId = dao.findTagByName(name)?.id
                    ?: dao.insertTag(ProblemTagEntity(name = name))
                dao.insertTagCrossRef(ProblemTagCrossRef(problemId, tagId))
            }
            if (row["inReview"]?.jsonPrimitive?.booleanOrNull == true) {
                database.reviewDao().upsert(
                    ReviewEntity(
                        problemId = problemId,
                        stage = 0,
                        dueAt = 0,
                        dueDayIndex = 0,
                        createdAt = 0,
                    ),
                )
            }
        }
    }

    private fun vectorQuery(query: kotlinx.serialization.json.JsonObject): LibraryQuery = LibraryQuery(
        search = query["search"]?.jsonPrimitive?.contentOrNull ?: "",
        status = query["status"]?.jsonPrimitive?.contentOrNull?.let { ProblemStatus.valueOf(it) },
        judge = query["judge"]?.jsonPrimitive?.contentOrNull,
        favoriteOnly = query["favoriteOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
        tag = query["tag"]?.jsonPrimitive?.contentOrNull,
        difficultyMin = query["difficultyMin"]?.jsonPrimitive?.intOrNull,
        difficultyMax = query["difficultyMax"]?.jsonPrimitive?.intOrNull,
        sort = when (query["sort"]?.jsonPrimitive?.contentOrNull) {
            "DIFFICULTY" -> LibrarySort.DIFFICULTY
            "TITLE" -> LibrarySort.TITLE
            "ATTEMPTS" -> LibrarySort.ATTEMPTS
            else -> LibrarySort.UPDATED
        },
    )
}
