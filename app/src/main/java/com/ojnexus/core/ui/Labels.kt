package com.ojnexus.core.ui

import androidx.annotation.StringRes
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.model.FailureCategory
import com.ojnexus.core.model.ProblemStatus
import com.ojnexus.core.model.ReviewResult
import com.ojnexus.core.model.TaskType
import com.ojnexus.core.model.TrainingType
import com.ojnexus.core.model.Verdict

/** UI label resources for domain enums — centralized so screens never map inline. */

@StringRes
fun FailureCategory.labelRes(): Int = when (this) {
    FailureCategory.THINKING -> R.string.cause_thinking
    FailureCategory.IMPLEMENTATION -> R.string.cause_implementation
    FailureCategory.BOUNDARY -> R.string.cause_boundary
    FailureCategory.COMPLEXITY -> R.string.cause_complexity
    FailureCategory.KNOWLEDGE_GAP -> R.string.cause_knowledge
    FailureCategory.READING -> R.string.cause_reading
    FailureCategory.CARELESS -> R.string.cause_careless
    FailureCategory.OTHER -> R.string.cause_other
}

fun FailureCategory.tone(): NexusTone = when (this) {
    FailureCategory.KNOWLEDGE_GAP -> NexusTone.Danger
    FailureCategory.COMPLEXITY -> NexusTone.Warning
    FailureCategory.CARELESS -> NexusTone.Warning
    FailureCategory.THINKING -> NexusTone.Accent
    else -> NexusTone.Neutral
}

@StringRes
fun ProblemStatus.labelRes(): Int = when (this) {
    ProblemStatus.UNSOLVED -> R.string.problem_status_unsolved
    ProblemStatus.ATTEMPTED -> R.string.problem_status_attempted
    ProblemStatus.SOLVED -> R.string.problem_status_solved
    ProblemStatus.REVIEW -> R.string.problem_status_review
}

fun ProblemStatus.tone(): NexusTone = when (this) {
    ProblemStatus.UNSOLVED -> NexusTone.Neutral
    ProblemStatus.ATTEMPTED -> NexusTone.Warning
    ProblemStatus.SOLVED -> NexusTone.Success
    ProblemStatus.REVIEW -> NexusTone.Accent
}

@StringRes
fun TaskType.labelRes(): Int = when (this) {
    TaskType.SOLVE -> R.string.task_type_solve
    TaskType.REVIEW -> R.string.task_type_review
    TaskType.READ -> R.string.task_type_read
    TaskType.UPSOLVE -> R.string.task_type_upsolve
}

@StringRes
fun TrainingType.labelRes(): Int = when (this) {
    TrainingType.PRACTICE -> R.string.session_type_practice
    TrainingType.FOCUS -> R.string.session_type_focus
    TrainingType.UPSOLVE -> R.string.session_type_upsolve
    TrainingType.REVIEW -> R.string.session_type_review
}

@StringRes
fun ReviewResult.labelRes(): Int = when (this) {
    ReviewResult.PASS -> R.string.review_result_pass
    ReviewResult.HARD -> R.string.review_result_hard
    ReviewResult.FAIL -> R.string.review_result_fail
    ReviewResult.SKIP -> R.string.review_result_skip
}

fun ReviewResult.tone(): NexusTone = when (this) {
    ReviewResult.PASS -> NexusTone.Success
    ReviewResult.HARD -> NexusTone.Warning
    ReviewResult.FAIL -> NexusTone.Danger
    ReviewResult.SKIP -> NexusTone.Neutral
}

@StringRes
fun Verdict.labelRes(): Int = when (this) {
    Verdict.AC -> R.string.verdict_ac
    Verdict.WA -> R.string.verdict_wa
    Verdict.TLE -> R.string.verdict_tle
    Verdict.MLE -> R.string.verdict_mle
    Verdict.RE -> R.string.verdict_re
    Verdict.CE -> R.string.verdict_ce
    Verdict.PE -> R.string.verdict_pe
    Verdict.OTHER -> R.string.verdict_other
}

fun Verdict.tone(): NexusTone = when (this) {
    Verdict.AC -> NexusTone.Success
    Verdict.WA -> NexusTone.Danger
    Verdict.TLE -> NexusTone.Warning
    Verdict.MLE -> NexusTone.Warning
    Verdict.RE -> NexusTone.Danger
    Verdict.CE -> NexusTone.Warning
    Verdict.PE -> NexusTone.Warning
    Verdict.OTHER -> NexusTone.Neutral
}
