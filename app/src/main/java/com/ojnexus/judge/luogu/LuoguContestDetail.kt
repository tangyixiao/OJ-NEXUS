package com.ojnexus.judge.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguContestDetailData

data class LuoguContestDetail(
    val id: Long,
    val name: String,
    val startTime: Long?,
    val endTime: Long?,
    val description: String,
    val totalParticipants: Int?,
    val problems: List<LuoguContestProblem>,
)

data class LuoguContestProblem(
    val index: String?,
    val pid: String,
    val name: String,
    val difficulty: Int?,
    val score: Int?,
)

object LuoguContestDetailMapper {
    fun toDomain(data: LuoguContestDetailData): LuoguContestDetail {
        val contest = requireNotNull(data.contest) { "Luogu contest payload is missing" }
        return LuoguContestDetail(
            id = contest.id,
            name = contest.name,
            startTime = contest.startTime,
            endTime = contest.endTime,
            description = contest.description.orEmpty(),
            totalParticipants = contest.totalParticipants,
            problems = data.contestProblems.mapNotNull { row ->
                val problem = row.problem ?: return@mapNotNull null
                LuoguContestProblem(
                    index = row.no,
                    pid = problem.pid,
                    name = problem.name,
                    difficulty = problem.difficulty,
                    score = row.score,
                )
            },
        )
    }
}

class LuoguContestDetailRepository(private val client: LuoguClient) {
    suspend fun fetch(contestId: String): LuoguContestDetail =
        LuoguContestDetailMapper.toDomain(client.fetchContest(contestId).data ?: error("Luogu contest data is missing"))
}
