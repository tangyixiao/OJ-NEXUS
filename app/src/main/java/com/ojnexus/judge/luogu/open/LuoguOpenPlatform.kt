package com.ojnexus.judge.luogu.open

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** The Open Platform credential pair; this is never the user's Luogu main-site password. */
data class OpenAppCredential(
    val user: String,
    val secret: String,
)

interface OpenAppCredentialStore {
    suspend fun read(): OpenAppCredential?
    suspend fun write(value: OpenAppCredential)
    suspend fun clear()
}

object OpenAppBasicAuth {
    fun header(credential: OpenAppCredential): String {
        require(credential.user.isNotBlank()) { "OpenApp user must not be blank" }
        require(credential.secret.isNotBlank()) { "OpenApp secret must not be blank" }
        require('\r' !in credential.user && '\n' !in credential.user) {
            "OpenApp user contains a line break"
        }
        require('\r' !in credential.secret && '\n' !in credential.secret) {
            "OpenApp secret contains a line break"
        }
        val raw = "${credential.user}:${credential.secret}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}

data class LuoguProblemJudgeRequest(
    val pid: String,
    val lang: String,
    val o2: Boolean,
    val code: String,
    val trackId: String? = null,
)

data class LuoguRunRequest(
    val input: String,
    val lang: String,
    val o2: Boolean,
    val code: String,
    val trackId: String? = null,
)

sealed interface LuoguOpenRequestValidation {
    data object Valid : LuoguOpenRequestValidation
    data object PidRequired : LuoguOpenRequestValidation
    data object LanguageRequired : LuoguOpenRequestValidation
    data object CodeRequired : LuoguOpenRequestValidation
    data object InputTooLarge : LuoguOpenRequestValidation
    data object TrackIdTooLong : LuoguOpenRequestValidation
}

object LuoguOpenRequestValidator {
    const val MAX_INPUT_LENGTH = 7168
    const val MAX_TRACK_ID_LENGTH = 64

    fun validateProblem(request: LuoguProblemJudgeRequest): LuoguOpenRequestValidation {
        if (request.pid.isBlank()) return LuoguOpenRequestValidation.PidRequired
        return validateCommon(request.lang, request.code, request.trackId)
    }

    fun validateRun(request: LuoguRunRequest): LuoguOpenRequestValidation {
        if (request.input.length > MAX_INPUT_LENGTH) return LuoguOpenRequestValidation.InputTooLarge
        return validateCommon(request.lang, request.code, request.trackId)
    }

    private fun validateCommon(
        language: String,
        code: String,
        trackId: String?,
    ): LuoguOpenRequestValidation = when {
        language.isBlank() -> LuoguOpenRequestValidation.LanguageRequired
        code.isBlank() -> LuoguOpenRequestValidation.CodeRequired
        trackId != null && trackId.length > MAX_TRACK_ID_LENGTH ->
            LuoguOpenRequestValidation.TrackIdTooLong
        else -> LuoguOpenRequestValidation.Valid
    }
}

@Serializable
internal data class LuoguAsyncResponseDto(
    val requestId: String? = null,
)

@Serializable
internal data class LuoguJudgeCallbackDto(
    val type: String? = null,
    val data: LuoguJudgeRecordDto? = null,
    val requestId: String? = null,
    val trackId: String? = null,
)

@Serializable
internal data class LuoguJudgeRecordDto(
    val compile: LuoguCompileResultDto? = null,
    val judge: LuoguScoringResultDto? = null,
    val run: LuoguRunResultDto? = null,
)

@Serializable
internal data class LuoguCompileResultDto(
    val success: Boolean? = null,
    val message: String? = null,
    val opt2: Boolean? = null,
)

@Serializable
internal data class LuoguScoringResultDto(
    val id: Long? = null,
    val status: Int? = null,
    val score: Int? = null,
    val time: Long? = null,
    val memory: Long? = null,
)

@Serializable
internal data class LuoguRunResultDto(
    val output: String? = null,
    val result: LuoguExecutionInfoDto? = null,
    val compile: LuoguCompileResultDto? = null,
)

@Serializable
internal data class LuoguExecutionInfoDto(
    val cpuTime: Long? = null,
    val memory: Long? = null,
    val exitCode: Int? = null,
    val signal: Int? = null,
    val excess: Int? = null,
)

internal interface LuoguOpenPlatformApi {
    @POST("judge/problem")
    suspend fun submitProblem(
        @Header("Authorization") authorization: String,
        @Body request: LuoguProblemJudgeRequestDto,
    ): Response<LuoguAsyncResponseDto>

    @GET("judge/result/{id}")
    suspend fun result(
        @Header("Authorization") authorization: String,
        @Path("id") requestId: String,
    ): Response<LuoguJudgeCallbackDto>

    @GET("judge/quotaAvailable")
    suspend fun quotaAvailable(
        @Header("Authorization") authorization: String,
    ): Response<LuoguQuotaAvailableResponseDto>
}

@Serializable
internal data class LuoguProblemJudgeRequestDto(
    val pid: String,
    val lang: String,
    val o2: Boolean,
    val code: String,
    val trackId: String? = null,
)

@Serializable
internal data class LuoguRunRequestDto(
    val input: String,
    val lang: String,
    val o2: Boolean,
    val code: String,
    val trackId: String? = null,
)

@Serializable
internal data class LuoguQuotaAvailableResponseDto(
    val quotas: List<LuoguQuotaDto> = emptyList(),
)

@Serializable
internal data class LuoguQuotaDto(
    val availablePoints: Long = 0,
    val createTime: Long = 0,
    val validAfter: Long = 0,
    val expireTime: Long = 0,
    val points: LuoguQuotaPointsDto = LuoguQuotaPointsDto(),
)

@Serializable
internal data class LuoguQuotaPointsDto(
    val max: Long = 0,
    val used: Long = 0,
)

data class LuoguOpenEvaluation(
    val requestId: String,
    val trackId: String?,
    val type: String?,
    val compileSuccess: Boolean?,
    val compileMessage: String?,
    val status: Int?,
    val score: Int?,
    val timeMs: Long?,
    val memoryKiB: Long?,
    val output: String?,
    val exitCode: Int?,
)

data class LuoguOpenSubmission(val requestId: String)

data class LuoguOpenQuota(
    val availablePoints: Long,
    val createTime: Long,
    val validAfter: Long,
    val expireTime: Long,
    val maxPoints: Long,
    val usedPoints: Long,
)

data class LuoguOpenQuotaSnapshot(val quotas: List<LuoguOpenQuota>) {
    val totalAvailablePoints: Long
        get() = quotas.sumOf { it.availablePoints }
}

private const val FOREGROUND_RESULT_POLL_ATTEMPTS = 8
private const val FOREGROUND_RESULT_POLL_DELAY_MS = 1_000L

/**
 * Bounded foreground-only result polling shared by the workspace and submission center.
 * Submission POSTs remain outside this helper and are never retried here.
 */
internal suspend fun pollLuoguOpenResult(
    requestId: String,
    fetch: suspend (String) -> LuoguOpenResult,
    delayForResult: (suspend (Long) -> Unit)? = null,
): LuoguOpenResult {
    repeat(FOREGROUND_RESULT_POLL_ATTEMPTS) { attempt ->
        when (val result = fetch(requestId)) {
            is LuoguOpenResult.Ready -> return result
            LuoguOpenResult.Pending -> {
                if (attempt < FOREGROUND_RESULT_POLL_ATTEMPTS - 1) {
                    if (delayForResult == null) {
                        delay(FOREGROUND_RESULT_POLL_DELAY_MS)
                    } else {
                        delayForResult(FOREGROUND_RESULT_POLL_DELAY_MS)
                    }
                }
            }
        }
    }
    return LuoguOpenResult.Pending
}

interface LuoguOpenQuotaReader {
    suspend fun fetchQuota(): LuoguOpenQuotaSnapshot
}

interface LuoguOpenGateway {
    /** True only when the concrete provider documents custom-input execution. */
    val supportsCustomInputRun: Boolean
        get() = true

    suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission
    suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission
    suspend fun fetchResult(requestId: String): LuoguOpenResult
}

sealed interface LuoguOpenResult {
    data object Pending : LuoguOpenResult
    data class Ready(val evaluation: LuoguOpenEvaluation) : LuoguOpenResult
}

sealed class LuoguOpenApiError(message: String) : Exception(message) {
    data object CredentialMissing : LuoguOpenApiError("OpenApp credential is not configured")
    data class InvalidRequest(val validation: LuoguOpenRequestValidation) :
        LuoguOpenApiError("Open Platform request is invalid: $validation")
    data object Unauthorized : LuoguOpenApiError("Open Platform authorization failed")
    data object Forbidden : LuoguOpenApiError("Open Platform access is forbidden")
    data object QuotaExceeded : LuoguOpenApiError("Open Platform quota is insufficient")
    data object NotFound : LuoguOpenApiError("Open Platform resource was not found")
    data object UnsupportedOperation : LuoguOpenApiError("Luogu Open Platform does not expose custom-input execution")
    data class Http(val status: Int) : LuoguOpenApiError("Open Platform HTTP $status")
    data class Network(val wrapped: IOException) : LuoguOpenApiError("Open Platform network error")
    data object MalformedResponse : LuoguOpenApiError("Open Platform returned no request ID")
}

class LuoguOpenPlatformClient internal constructor(
    private val api: LuoguOpenPlatformApi,
    private val credentialStore: OpenAppCredentialStore,
) : LuoguOpenGateway, LuoguOpenQuotaReader {
    override val supportsCustomInputRun: Boolean = false

    override suspend fun submitProblem(request: LuoguProblemJudgeRequest): LuoguOpenSubmission =
        executeSubmission(
            validation = LuoguOpenRequestValidator.validateProblem(request),
        ) { authorization ->
            api.submitProblem(
                authorization,
                LuoguProblemJudgeRequestDto(
                    request.pid.trim(),
                    request.lang.trim(),
                    request.o2,
                    request.code,
                    request.trackId,
                ),
            )
        }.let { LuoguOpenSubmission(it.requestId ?: throw LuoguOpenApiError.MalformedResponse) }

    override suspend fun run(request: LuoguRunRequest): LuoguOpenSubmission =
        throw LuoguOpenApiError.UnsupportedOperation

    override suspend fun fetchResult(requestId: String): LuoguOpenResult {
        if (requestId.isBlank()) throw LuoguOpenApiError.InvalidRequest(LuoguOpenRequestValidation.CodeRequired)
        val authorization = authorizationHeader()
        val response = try {
            api.result(authorization, requestId)
        } catch (error: IOException) {
            throw LuoguOpenApiError.Network(error)
        }
        if (response.code() == 204) return LuoguOpenResult.Pending
        val callback = bodyOrThrow(response)
        val data = callback.data ?: throw LuoguOpenApiError.MalformedResponse
        return LuoguOpenResult.Ready(
            LuoguOpenEvaluation(
                requestId = callback.requestId ?: requestId,
                trackId = callback.trackId,
                type = callback.type,
                compileSuccess = data.compile?.success,
                compileMessage = data.compile?.message,
                status = data.judge?.status,
                score = data.judge?.score,
                timeMs = data.judge?.time ?: data.run?.result?.cpuTime,
                memoryKiB = data.judge?.memory ?: data.run?.result?.memory,
                output = data.run?.output,
                exitCode = data.run?.result?.exitCode,
            ),
        )
    }

    override suspend fun fetchQuota(): LuoguOpenQuotaSnapshot {
        val response = try {
            api.quotaAvailable(authorizationHeader())
        } catch (error: IOException) {
            throw LuoguOpenApiError.Network(error)
        }
        return bodyOrThrow(response).quotas.map { quota ->
            LuoguOpenQuota(
                availablePoints = quota.availablePoints,
                createTime = quota.createTime,
                validAfter = quota.validAfter,
                expireTime = quota.expireTime,
                maxPoints = quota.points.max,
                usedPoints = quota.points.used,
            )
        }.let(::LuoguOpenQuotaSnapshot)
    }

    private suspend fun <T> executeSubmission(
        validation: LuoguOpenRequestValidation,
        call: suspend (String) -> Response<T>,
    ): T {
        if (validation != LuoguOpenRequestValidation.Valid) {
            throw LuoguOpenApiError.InvalidRequest(validation)
        }
        val authorization = authorizationHeader()
        val response = try {
            call(authorization)
        } catch (error: IOException) {
            throw LuoguOpenApiError.Network(error)
        }
        return bodyOrThrow(response)
    }

    private suspend fun authorizationHeader(): String =
        credentialStore.read()?.let(OpenAppBasicAuth::header)
            ?: throw LuoguOpenApiError.CredentialMissing

    private fun <T> bodyOrThrow(response: Response<T>): T {
        if (!response.isSuccessful) {
            throw when (response.code()) {
                401 -> LuoguOpenApiError.Unauthorized
                403 -> LuoguOpenApiError.Forbidden
                402 -> LuoguOpenApiError.QuotaExceeded
                404 -> LuoguOpenApiError.NotFound
                else -> LuoguOpenApiError.Http(response.code())
            }
        }
        return response.body() ?: throw LuoguOpenApiError.MalformedResponse
    }
}
