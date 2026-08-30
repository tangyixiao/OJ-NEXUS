package com.ojnexus.judge.luogu

import com.ojnexus.core.model.JudgeId
import com.ojnexus.judge.AccountBinding
import com.ojnexus.judge.AccountBindingError
import com.ojnexus.judge.AccountVerificationState
import com.ojnexus.judge.DataSourceReliability
import com.ojnexus.judge.JudgeAccountConnector
import kotlinx.coroutines.CancellationException

class LuoguAccountConnector(
    private val adapter: LuoguAdapter,
) : JudgeAccountConnector {
    override val judgeId = JudgeId.LUOGU

    override suspend fun bind(rawHandle: String): AccountBinding {
        val trimmed = rawHandle.trim()
        if (!HANDLE.matches(trimmed)) throw AccountBindingError.InvalidHandle()
        val user = try {
            adapter.searchUser(trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LuoguApiError) {
            throw AccountBindingError.Unavailable(e.message, e)
        } catch (e: Exception) {
            throw AccountBindingError.Unavailable(e.message, e)
        } ?: throw AccountBindingError.NotFound("Luogu user not found")

        if (user.name != trimmed) throw AccountBindingError.NotFound("Luogu user not found")
        return AccountBinding(
            storedHandle = trimmed,
            canonicalHandle = user.name,
            verificationState = AccountVerificationState.VERIFIED,
            reliability = DataSourceReliability.EXPERIMENTAL,
        )
    }

    private companion object {
        val HANDLE = Regex("[A-Za-z0-9_]{1,20}")
    }
}
