package com.ojnexus.feature.luogu

import com.ojnexus.judge.luogu.api.dto.LuoguTrainingProblemDto
import com.ojnexus.judge.luogu.api.dto.LuoguTrainingSummaryDto

/** Pure helpers extracted from the training screens so the row logic is unit-testable. */

internal fun trainingCatalogContext(
    training: LuoguTrainingSummaryDto,
    problemCountLabel: String,
): String = listOfNotNull(
    training.provider?.name,
    problemCountLabel.takeIf { it.isNotBlank() },
).joinToString(" · ")

internal fun trainingProblemTitle(problem: LuoguTrainingProblemDto): String =
    problem.name.ifBlank { problem.pid }

internal fun ladderPositionLabel(index: Int): String = (index + 1).toString().padStart(2, '0')
