package com.ojnexus.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.core.database.entity.ProblemEntity
import com.ojnexus.core.database.relation.ProblemWithTagsPojo
import com.ojnexus.core.data.repository.LIBRARY_PAGE_SIZE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 137 bounded-query acceptance (G-A): at 10k and 50k synthetic rows the
 * library path stays budgeted — the first page is a SQL-bounded LIMIT load, the
 * statistics come from SQL COUNT (never the page), and inserts/deletes during an
 * active filter neither duplicate keys nor lose the anchor. The budgets prove
 * boundedness (a full-list regression would blow them), not latency claims.
 * Robolectric in-memory Room; budgets are generous so hosted CI cannot flake on
 * runner speed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProblemQueryPerfTest {

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

    /** [count] ordinary rows plus [matches] rows titled with the filter probe "alpha needle". */
    private suspend fun seed(total: Int, matches: Int) {
        val dao = database.problemDao()
        database.withTransaction {
            repeat(total) { n ->
                dao.insert(
                    ProblemEntity(
                        judge = "codeforces",
                        externalId = "perf-${n.toString().padStart(6, '0')}",
                        title = "Perf Problem $n",
                        titleLower = "perf problem $n",
                        difficulty = 800 + (n % 20) * 100,
                        createdAt = 1_000_000L + n,
                        updatedAt = 1_000_000L + n,
                    ),
                )
            }
            repeat(matches) { n ->
                dao.insert(
                    ProblemEntity(
                        judge = "codeforces",
                        externalId = "alpha-${n.toString().padStart(6, '0')}",
                        title = "alpha needle $n",
                        titleLower = "alpha needle $n",
                        difficulty = 1200,
                        createdAt = 2_000_000L + n,
                        updatedAt = 2_000_000L + n,
                    ),
                )
            }
        }
    }

    private suspend fun firstPage(query: LibraryQuery, loadSize: Int): androidx.paging.PagingSource.LoadResult.Page<Int, ProblemWithTagsPojo> {
        val source = database.problemDao().pagingLibrary(LibraryQueryBuilder.pagingLibrary(query))
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false),
        )
        return result as androidx.paging.PagingSource.LoadResult.Page
    }

    @Test
    fun `10k library first page is bounded and stats are sql-derived`() = runBlocking {
        seed(total = 10_000, matches = 250)
        val query = LibraryQuery(search = "alpha needle")
        val started = System.nanoTime()
        val page = firstPage(query, loadSize = LIBRARY_PAGE_SIZE)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        // Budgeted first page: the SQL LIMIT keeps the load flat at 10k rows.
        assertTrue("first page took ${elapsedMs}ms (budget 5000ms)", elapsedMs < 5_000)
        assertEquals("page must be SQL-bounded, never the full list", 60, page.data.size)
        // Statistics come from SQL COUNT, not the page.
        assertEquals(250, database.problemDao().observeMatchCount(query).first().toInt())
        assertEquals(10_250, database.problemDao().observeTotalCount().first())
        // UPDATED sorts descending: the newest alpha row leads the page.
        assertEquals("alpha-000249", page.data.first().problem.externalId)
    }

    @Test
    fun `50k library first page is bounded and stats are sql-derived`() = runBlocking {
        seed(total = 50_000, matches = 400)
        val query = LibraryQuery(search = "alpha needle")
        val started = System.nanoTime()
        val page = firstPage(query, loadSize = LIBRARY_PAGE_SIZE)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("first page took ${elapsedMs}ms (budget 8000ms)", elapsedMs < 8_000)
        assertEquals(60, page.data.size)
        assertEquals(400, database.problemDao().observeMatchCount(query).first().toInt())
        assertEquals(50_400, database.problemDao().observeTotalCount().first())
    }

    @Test
    fun `insert and delete during an active filter keep keys unique`() = runBlocking {
        seed(total = 300, matches = 120)
        val query = LibraryQuery(search = "alpha needle")
        val dao = database.problemDao()
        val before = firstPage(query, loadSize = LIBRARY_PAGE_SIZE).data
            .map { it.problem.id }
        assertEquals(before, before.distinct())

        // Insert a row that sorts first under the filter (newest updated_at); delete one visible row.
        dao.insert(
            ProblemEntity(
                judge = "codeforces",
                externalId = "alpha-999999",
                title = "alpha needle anchor",
                titleLower = "alpha needle anchor",
                difficulty = 1200,
                createdAt = 4_000_000L,
                updatedAt = 4_000_000L,
            ),
        )
        val deletedId = before.first()
        database.problemDao().delete(database.problemDao().findById(deletedId)!!)

        val after = firstPage(query, loadSize = LIBRARY_PAGE_SIZE).data
        val ids = after.map { it.problem.id }
        assertEquals("no duplicate keys after insert+delete", ids, ids.distinct())
        assertFalse("deleted row must not resurface", ids.contains(deletedId))
        assertEquals(
            "the inserted anchor sorts first under the filter's ordering",
            "alpha-999999",
            after.first().problem.externalId,
        )
    }
}
