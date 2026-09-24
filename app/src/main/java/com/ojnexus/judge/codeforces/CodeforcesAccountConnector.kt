package com.ojnexus.judge.codeforces

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AccountBinding
import com.ojnexus.judge.AccountBindingError
import com.ojnexus.judge.AccountVerificationState
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAccountConnector
import kotlinx.coroutines.CancellationException

class CodeforcesAccountConnector(
    private val adapter: CodeforcesAdapter,
) : JudgeAccountConnector {
    override val judgeId = JudgeId.CODEFORCES

    override suspend fun bind(rawHandle: String): AccountBinding {
        val trimmed = rawHandle.trim()
        if (trimmed.isEmpty()) throw AccountBindingError.InvalidHandle()
        val profile = try {
            adapter.fetchProfile(trimmed)
        } catch (e: CodeforcesApiError.UserNotFound) {
            throw AccountBindingError.NotFound(e.rawComment)
        } catch (e: CodeforcesApiError) {
            throw AccountBindingError.Unavailable(e.rawComment, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountBindingError.Unavailable(e.message, e)
        }
        return AccountBinding(
            storedHandle = rawHandle,
            canonicalHandle = profile.handle.trim(),
            verificationState = AccountVerificationState.VERIFIED,
            reliability = DataSourceReliability.OFFICIAL,
        )
    }
}
