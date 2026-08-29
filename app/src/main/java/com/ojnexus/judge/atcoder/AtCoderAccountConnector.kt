package com.ojnexus.judge.atcoder

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AccountBinding
import com.ojnexus.judge.AccountBindingError
import com.ojnexus.judge.AccountVerificationState
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAccountConnector
import kotlinx.coroutines.CancellationException

/** Soft binding: empty/unavailable community data cannot prove that a valid user is absent. */
class AtCoderAccountConnector(
    private val adapter: AtCoderAdapter,
) : JudgeAccountConnector {
    override val judgeId = JudgeId.ATCODER

    override suspend fun bind(rawHandle: String): AccountBinding {
        val trimmed = rawHandle.trim()
        if (!HANDLE.matches(trimmed)) throw AccountBindingError.InvalidHandle()
        val submissions = try {
            adapter.fetchSubmissions(trimmed, 0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val confirmed = submissions?.firstOrNull { it.userId == trimmed }
        return AccountBinding(
            storedHandle = trimmed,
            canonicalHandle = confirmed?.userId ?: trimmed,
            verificationState = if (confirmed == null) {
                AccountVerificationState.UNVERIFIED
            } else {
                AccountVerificationState.VERIFIED
            },
            reliability = DataSourceReliability.COMMUNITY,
        )
    }

    private companion object {
        val HANDLE = Regex("[A-Za-z0-9_]{1,20}")
    }
}
