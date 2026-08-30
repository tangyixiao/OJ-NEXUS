package com.ojnexus.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusRadius
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.designsystem.component.NexusDivider
import com.ojnexus.core.designsystem.component.NexusMetric
import com.ojnexus.core.designsystem.component.NexusSection
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.designsystem.component.NexusTopBar
import com.ojnexus.core.domain.AchievementEngine
import com.ojnexus.core.domain.AchievementEvidence
import com.ojnexus.core.domain.AchievementState
import com.ojnexus.core.model.JudgeId
import com.ojnexus.core.ui.ContainerViewModelFactory
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.core.ui.Loadable
import com.ojnexus.core.ui.formatCount
import com.ojnexus.core.ui.formatDays
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.PlayerCardImageData
import com.ojnexus.core.ui.PlayerCardShare
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val problems: Int,
    val solved: Int,
    val attempts: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val maxSolvedDifficulty: Int?,
    val connections: com.ojnexus.core.data.repository.JudgeConnectionSnapshot,
    val cfAccount: com.ojnexus.core.database.entity.JudgeAccountEntity?,
    val cfProfile: com.ojnexus.core.database.entity.JudgeProfileEntity?,
    val ratedContests: Int,
    val achievements: List<AchievementState>,
)

class ProfileViewModel(
    analyticsRepository: com.ojnexus.core.data.repository.AnalyticsRepository,
    judgeDataRepository: com.ojnexus.core.data.repository.JudgeDataRepository,
) : ViewModel() {

    val state: StateFlow<Loadable<ProfileUiState>> = combine(
        analyticsRepository.observeTotals(),
        analyticsRepository.observeStreaks(days = 365),
        analyticsRepository.observeDifficultyCounts(),
        judgeDataRepository.observeConnections(),
        analyticsRepository.observeRatingChanges(com.ojnexus.core.model.JudgeId.CODEFORCES.id),
    ) { totals, streaks, difficultyCounts, connections, ratingChanges ->
        val account = connections.accounts[JudgeId.CODEFORCES]
        Loadable.Ready(
            ProfileUiState(
                problems = totals.problems,
                solved = totals.solved,
                attempts = totals.attempts,
                activeDays = streaks.activeDays,
                currentStreak = streaks.current,
                longestStreak = streaks.longest,
                maxSolvedDifficulty = difficultyCounts.mapNotNull { it.first }.maxOrNull(),
                connections = connections,
                cfAccount = account,
                cfProfile = connections.profiles[JudgeId.CODEFORCES],
                ratedContests = ratingChanges.size,
                achievements = AchievementEngine.evaluate(
                    AchievementEvidence(
                        solved = totals.solved,
                        activeDays = streaks.activeDays,
                        currentStreak = streaks.current,
                        maxSolvedDifficulty = difficultyCounts.mapNotNull { it.first }.maxOrNull(),
                        ratedContests = ratingChanges.size,
                    ),
                ),
            ),
        )
    }
        .catch<Loadable<ProfileUiState>> { emit(Loadable.Failed(it.message ?: "Load failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)
}

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit = {}) {    val container = LocalAppContainer.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ProfileViewModel>(
        factory = ContainerViewModelFactory(container) {
            ProfileViewModel(
                analyticsRepository = it.analyticsRepository,
                judgeDataRepository = it.judgeDataRepository,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        NexusTopBar(
            title = stringResource(R.string.nav_profile),
            trailing = {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = NexusTheme.typography.sectionLabel,
                    color = NexusTheme.colors.accent,
                    modifier = Modifier.clickable { onOpenSettings() },
                )
            },
        )
        when (val s = state) {
            Loadable.Loading -> Box(Modifier.fillMaxSize())
            is Loadable.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = s.message, style = NexusTheme.typography.data, color = NexusTheme.colors.danger)
            }
            is Loadable.Ready -> {
                val context = LocalContext.current
                ProfileContent(s.value) { data -> PlayerCardShare.share(context, data) }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onShareCard: (PlayerCardImageData) -> Unit,
) {
    val colors = NexusTheme.colors
    val cardImageData = state.toCardImageData()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(NexusSpacing.md))

        // PLAYER CARD — local identity plus the connected public judge profile.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, NexusRadius.md)
                .padding(NexusSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = NexusTheme.typography.displayData,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.profile_role),
                style = NexusTheme.typography.sectionLabel,
                color = colors.textTertiary,
            )
            Text(
                text = stringResource(R.string.profile_share_card),
                style = NexusTheme.typography.sectionLabel,
                color = colors.accent,
                modifier = Modifier.align(Alignment.End).clickable { onShareCard(cardImageData) },
            )
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            NexusDivider()
            Spacer(modifier = Modifier.height(NexusSpacing.xxs))
            listOf(JudgeId.CODEFORCES, JudgeId.ATCODER).forEachIndexed { index, judge ->
                    val account = state.connections.accounts[judge]
                    val profile = state.connections.profiles[judge]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NexusSize.tableRowHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(judge.displayName, style = NexusTheme.typography.dataSmall, color = colors.textPrimary)
                            account?.let {
                                Text(it.canonicalHandle, style = NexusTheme.typography.dataSmall, color = colors.accent)
                            }
                        }
                        when {
                            account == null -> Text(
                                stringResource(R.string.judge_not_linked),
                                style = NexusTheme.typography.dataSmall,
                                color = colors.textTertiary,
                            )
                            profile?.rating != null -> Column(horizontalAlignment = Alignment.End) {
                                Text(profile.rating.toString(), style = NexusTheme.typography.data, color = colors.textPrimary)
                                profile.rank?.let { NexusTag(text = it.uppercase(), tone = NexusTone.Neutral) }
                            }
                            else -> Text(
                                stringResource(R.string.settings_rating_unavailable),
                                style = NexusTheme.typography.dataSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    if (index == 0) {
                        NexusDivider(insetEnd = NexusSpacing.xxs)
                    }
                }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NexusSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.cfAccount != null) {
                        stringResource(R.string.rating_rated_contests)
                    } else {
                        stringResource(R.string.profile_phase2_note)
                    },
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (state.cfAccount != null) {
                    Text(
                        text = state.ratedContests.toString(),
                        style = NexusTheme.typography.dataSmall,
                        color = colors.textSecondary,
                    )
                } else {
                    NexusTag(
                        text = stringResource(R.string.dash_not_connected),
                        tone = NexusTone.Neutral,
                    )
                }
            }
        }

        SectionGap()

        // GLOBAL
        NexusSection(label = stringResource(R.string.profile_section_global)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.profile_stat_solved),
                    value = formatCount(state.solved),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.profile_stat_submissions),
                    value = formatCount(state.attempts),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.profile_stat_active_days),
                    value = formatCount(state.activeDays),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(NexusSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                NexusMetric(
                    label = stringResource(R.string.profile_stat_streak),
                    value = formatDays(state.currentStreak),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.dash_streak_longest),
                    value = formatDays(state.longestStreak),
                    modifier = Modifier.weight(1f),
                )
                MetricSeparator()
                NexusMetric(
                    label = stringResource(R.string.profile_stat_max_diff),
                    value = state.maxSolvedDifficulty?.toString()
                        ?: stringResource(R.string.problems_no_value),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionGap()

        NexusSection(label = stringResource(R.string.profile_section_achievements)) {
            val unlocked = state.achievements.filter { it.unlocked }
            if (unlocked.isEmpty()) {
                Text(
                    text = stringResource(R.string.achievement_none),
                    style = NexusTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
            } else {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NexusSpacing.xxs)) {
                    unlocked.forEach { achievement ->
                        NexusTag(
                            text = stringResource(achievement.id.labelRes()),
                            tone = NexusTone.Success,
                            selected = true,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(NexusSpacing.xxl))
    }
}

@Composable
private fun ProfileUiState.toCardImageData(): PlayerCardImageData = PlayerCardImageData(
    title = stringResource(R.string.app_name),
    role = stringResource(R.string.profile_role),
    cardLabel = stringResource(R.string.profile_card_label),
    achievementsLabel = stringResource(R.string.profile_section_achievements),
    solvedLabel = stringResource(R.string.profile_stat_solved),
    solvedValue = solved.toString(),
    attemptsLabel = stringResource(R.string.profile_stat_submissions),
    attemptsValue = attempts.toString(),
    activeDaysLabel = stringResource(R.string.profile_stat_active_days),
    activeDaysValue = activeDays.toString(),
    streakLabel = stringResource(R.string.profile_stat_streak),
    streakValue = currentStreak.toString(),
    maxDifficultyLabel = stringResource(R.string.profile_stat_max_diff),
    maxDifficultyValue = maxSolvedDifficulty?.toString() ?: stringResource(R.string.problems_no_value),
    achievements = achievements.filter { it.unlocked }.map { stringResource(it.id.labelRes()) },
    themeSlot = NexusTheme.themeSlot,
)

@Composable
private fun SectionGap() {
    Spacer(modifier = Modifier.height(NexusSpacing.md))
    NexusDivider()
    Spacer(modifier = Modifier.height(NexusSpacing.md))
}

@Composable
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(NexusTheme.colors.border),
    )
}
