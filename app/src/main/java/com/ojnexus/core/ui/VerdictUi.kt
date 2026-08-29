package com.ojnexus.core.ui

import androidx.annotation.StringRes
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusTone
import com.ojnexus.core.model.Verdict

/**
 * Presentation mapping for [Verdict]: tone (color) and localized label resource.
 * Kept out of the domain layer and out of the design system — this is the glue between them.
 */
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
