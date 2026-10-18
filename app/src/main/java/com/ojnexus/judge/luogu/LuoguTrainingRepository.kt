package com.ojnexus.judge.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguTrainingDetailDto
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingSummaryDto
import kotlinx.coroutines.CancellationException

/**
 * Read-only access to Luogu's PUBLIC training catalog (no auth, no cookies, no team pages).
 * The list is bounded to the first page (18 official entries); detail loads one training.
 */
class LuoguTrainingRepository(private val client: LuoguClient) {

    suspend fun list(): List<LuoguTrainingSummaryDto> {
        val html = try {
            client.fetchTrainingListHtml()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LuoguApiError) {
            throw e
        }
        val body = html.use { it.string() }
        val payload = LuoguTrainingCodec.parseList(body)
            ?: throw LuoguApiError.ParseError(
                IllegalStateException("Luogu training list page has no lentille payload"),
            )
        return payload.data?.trainings?.result ?: emptyList()
    }

    suspend fun training(id: Long): LuoguTrainingDetailDto {
        val html = try {
            client.fetchTrainingHtml(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LuoguApiError) {
            throw e
        }
        val body = html.use { it.string() }
        val payload = LuoguTrainingCodec.parseDetail(body)
            ?: throw LuoguApiError.ParseError(
                IllegalStateException("Luogu training page has no lentille payload"),
            )
        return payload.data?.training
            ?: throw LuoguApiError.ParseError(
                IllegalStateException("Luogu training payload is missing the training node"),
            )
    }
}
