package com.ojnexus.feature.settings

import com.ojnexus.judge.luogu.open.LuoguOpenApiError
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaReader
import com.ojnexus.judge.luogu.open.LuoguOpenQuotaSnapshot
import com.ojnexus.judge.luogu.open.OpenAppCredential
import com.ojnexus.judge.luogu.open.OpenAppCredentialStore

internal sealed interface OpenAppCredentialVerification {
    data class Verified(val quota: LuoguOpenQuotaSnapshot?) : OpenAppCredentialVerification
    data class Rejected(val error: OpenAppQuotaError) : OpenAppCredentialVerification
    data class Unverified(val error: OpenAppQuotaError) : OpenAppCredentialVerification
}

/** Stores an OpenApp token and verifies it without ever logging or backing up its values. */
internal suspend fun verifyAndStoreOpenAppCredential(
    store: OpenAppCredentialStore,
    quotaReader: LuoguOpenQuotaReader?,
    credential: OpenAppCredential,
): OpenAppCredentialVerification {
    store.write(credential)
    if (quotaReader == null) return OpenAppCredentialVerification.Verified(quota = null)
    return try {
        OpenAppCredentialVerification.Verified(quotaReader.fetchQuota())
    } catch (error: LuoguOpenApiError) {
        val mapped = error.toOpenAppQuotaError()
        if (error is LuoguOpenApiError.Unauthorized || error is LuoguOpenApiError.Forbidden) {
            runCatching { store.clear() }
            OpenAppCredentialVerification.Rejected(mapped)
        } else {
            OpenAppCredentialVerification.Unverified(mapped)
        }
    } catch (_: Exception) {
        OpenAppCredentialVerification.Unverified(OpenAppQuotaError.API)
    }
}

internal fun LuoguOpenApiError.toOpenAppQuotaError(): OpenAppQuotaError = when (this) {
    LuoguOpenApiError.CredentialMissing -> OpenAppQuotaError.CREDENTIAL_MISSING
    LuoguOpenApiError.Unauthorized -> OpenAppQuotaError.UNAUTHORIZED
    LuoguOpenApiError.Forbidden -> OpenAppQuotaError.FORBIDDEN
    LuoguOpenApiError.QuotaExceeded -> OpenAppQuotaError.QUOTA_EXCEEDED
    LuoguOpenApiError.NotFound -> OpenAppQuotaError.NOT_FOUND
    is LuoguOpenApiError.Network -> OpenAppQuotaError.NETWORK
    else -> OpenAppQuotaError.API
}
