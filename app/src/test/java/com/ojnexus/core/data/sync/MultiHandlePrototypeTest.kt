package com.ojnexus.core.data.sync

import com.ojnexus.core.database.entity.JudgeAccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiHandlePrototypeTest {

    private fun account(
        id: Long,
        judge: String = "codeforces",
        canonicalHandle: String,
        enabled: Boolean = true,
    ) = JudgeAccountEntity(
        id = id,
        judge = judge,
        handle = canonicalHandle,
        canonicalHandle = canonicalHandle,
        connectedAt = 1L,
        updatedAt = 1L,
        enabled = enabled,
    )

    @Test
    fun `same canonical handle re-binds in place`() {
        val decision = MultiHandlePrototype.planConnect(
            accounts = listOf(account(1, canonicalHandle = "tourist")),
            judge = "codeforces",
            requestedCanonical = "tourist",
        )

        assertEquals(MultiHandlePrototype.ConnectDecision.ReuseExisting, decision)
    }

    @Test
    fun `second distinct handle below cap inserts a disabled secondary`() {
        val decision = MultiHandlePrototype.planConnect(
            accounts = listOf(account(1, canonicalHandle = "tourist")),
            judge = "codeforces",
            requestedCanonical = "Petr",
        )

        assertEquals(MultiHandlePrototype.ConnectDecision.AddSecondary, decision)
    }

    @Test
    fun `cap reached replaces every other account of the judge`() {
        val decision = MultiHandlePrototype.planConnect(
            accounts = listOf(
                account(1, canonicalHandle = "tourist"),
                account(2, canonicalHandle = "Petr", enabled = false),
            ),
            judge = "codeforces",
            requestedCanonical = "Um_nik",
        )

        assertEquals(
            MultiHandlePrototype.ConnectDecision.ReplaceActive(listOf(1L, 2L)),
            decision,
        )
    }

    @Test
    fun `planner ignores accounts of other judges`() {
        val decision = MultiHandlePrototype.planConnect(
            accounts = listOf(
                account(1, judge = "atcoder", canonicalHandle = "tourist"),
                account(2, judge = "atcoder", canonicalHandle = "chokudai", enabled = false),
            ),
            judge = "codeforces",
            requestedCanonical = "Um_nik",
        )

        assertEquals(MultiHandlePrototype.ConnectDecision.AddSecondary, decision)
    }

    @Test
    fun `work is stale when its account no longer exists for the judge`() {
        val accounts = listOf(account(1, canonicalHandle = "tourist"))

        assertTrue(
            MultiHandlePrototype.isWorkStaleForAccount(accounts, "codeforces", workAccountId = 9),
        )
        assertFalse(
            MultiHandlePrototype.isWorkStaleForAccount(accounts, "codeforces", workAccountId = 1),
        )
        // A disabled (secondary) account still answers its own work — only absence is stale.
        val withSecondary = accounts + account(2, canonicalHandle = "Petr", enabled = false)
        assertFalse(
            MultiHandlePrototype.isWorkStaleForAccount(withSecondary, "codeforces", workAccountId = 2),
        )
    }

    @Test
    fun `switching keeps the target and removes the judge's other accounts`() {
        val removed = MultiHandlePrototype.accountsToRemoveOnSwitch(
            accounts = listOf(
                account(1, canonicalHandle = "tourist"),
                account(2, canonicalHandle = "Petr", enabled = false),
                account(3, judge = "atcoder", canonicalHandle = "chokudai"),
            ),
            judge = "codeforces",
            targetAccountId = 2,
        )

        assertEquals(listOf(1L), removed)
    }
}
