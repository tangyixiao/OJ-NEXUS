package com.ojnexus.core.ui.submission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusTag
import com.ojnexus.core.ui.UrlOpener
import com.ojnexus.core.ui.labelRes
import com.ojnexus.core.ui.tone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Unified bottom sheet rendering any judge's submission: verdict, time/memory, problem link and
 * the note shortcut. Every caller converts its judge-specific record into
 * [SubmissionDetailUiModel]; this component never inspects judge types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailSheet(
    detail: SubmissionDetailUiModel,
    onDismiss: () -> Unit,
    onOpenNotes: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SubmissionDetailContent(
            detail = detail,
            onOpenNotes = onOpenNotes,
        )
    }
}

@Composable
fun SubmissionDetailContent(
    detail: SubmissionDetailUiModel,
    onOpenNotes: (() -> Unit)?,
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val openSubmissionLabel = stringResource(R.string.submission_detail_open_submission)
    val openProblemLabel = stringResource(R.string.submission_detail_open_problem)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexusSpacing.lg)
            .padding(bottom = NexusSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
    ) {
        // Header: judge + verdict tags.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
        ) {
            NexusTag(text = detail.judgeId.uppercase(), selected = true)
            detail.verdict?.let { verdict ->
                NexusTag(
                    text = stringResource(verdict.labelRes()),
                    tone = verdict.tone(),
                    selected = true,
                )
            }
        }
        detail.rawVerdict?.takeIf { it.isNotBlank() }?.let { raw ->
            Text(
                text = raw,
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        }

        // Core metrics.
        SubmissionDetailMetricsRow(detail = detail)

        // Language + timestamp.
        detail.language?.takeIf { it.isNotBlank() }?.let { language ->
            SubmissionDetailLine(
                label = stringResource(R.string.submission_detail_language),
                value = language,
            )
        }
        detail.timestampEpochMs?.let { timestamp ->
            SubmissionDetailLine(
                label = stringResource(R.string.submission_detail_submitted_at),
                value = formatSubmissionInstant(timestamp),
            )
        }

        // Problem link row.
        if (detail.problemUrl != null || detail.problemId != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
            ) {
                val label = listOfNotNull(detail.problemId, detail.problemTitle)
                    .joinToString(" · ")
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        style = NexusTheme.typography.data,
                        color = colors.textPrimary,
                        maxLines = 2,
                    )
                }
                if (detail.problemUrl != null) {
                    Text(
                        text = openProblemLabel,
                        style = NexusTheme.typography.dataSmall,
                        color = colors.accent,
                        modifier = Modifier
                            .clickableSubmissionLink(openProblemLabel) {
                                UrlOpener.open(context, detail.problemUrl)
                            },
                    )
                }
            }
        }

        // Judge-side submission link.
        if (detail.submissionUrl != null) {
            Text(
                text = openSubmissionLabel,
                style = NexusTheme.typography.dataSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickableSubmissionLink(openSubmissionLabel) {
                        UrlOpener.open(context, detail.submissionUrl)
                    },
            )
        }

        // Extra rows (compile messages, outputs, scores...).
        detail.extraRows.forEach { row ->
            SubmissionDetailLine(label = row.label, value = row.value)
        }

        // Note shortcut.
        if (onOpenNotes != null) {
            val noteLabel = stringResource(R.string.submission_detail_open_notes)
            Text(
                text = noteLabel,
                style = NexusTheme.typography.dataSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickableSubmissionLink(noteLabel) { onOpenNotes() },
            )
        }
    }
}

@Composable
private fun SubmissionDetailMetricsRow(detail: SubmissionDetailUiModel) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.md),
    ) {
        detail.executionTimeMs?.let { time ->
            MetricCell(
                label = stringResource(R.string.submission_detail_time),
                value = formatSubmissionTime(time),
            )
        }
        detail.memoryKiB?.let { memory ->
            MetricCell(
                label = stringResource(R.string.submission_detail_memory),
                value = formatSubmissionMemory(memory),
            )
        }
        detail.score?.let { score ->
            MetricCell(
                label = stringResource(R.string.submission_detail_score),
                value = score.toString(),
            )
        }
        if (detail.executionTimeMs == null && detail.memoryKiB == null && detail.score == null) {
            Text(
                text = stringResource(R.string.submission_detail_metrics_unavailable),
                style = NexusTheme.typography.dataSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        Text(
            text = value,
            style = NexusTheme.typography.data,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SubmissionDetailLine(label: String, value: String) {
    val colors = NexusTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xxs)) {
        Text(
            text = label,
            style = NexusTheme.typography.sectionLabel,
            color = colors.textTertiary,
        )
        Text(
            text = value,
            style = NexusTheme.typography.dataSmall,
            color = colors.textSecondary,
        )
    }
}

private fun Modifier.clickableSubmissionLink(
    description: String,
    onClick: () -> Unit,
): Modifier = this
    .clickable(
        role = Role.Button,
        onClickLabel = description,
        onClick = onClick,
    )
    .semantics { contentDescription = description }

internal fun formatSubmissionTime(ms: Long): String = when {
    ms >= 1000 -> "%.2f s".format(ms / 1000.0)
    else -> "$ms ms"
}

internal fun formatSubmissionMemory(kib: Long): String = when {
    kib >= 1024 -> "%.1f MiB".format(kib / 1024.0)
    else -> "$kib KiB"
}

internal fun formatSubmissionInstant(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))