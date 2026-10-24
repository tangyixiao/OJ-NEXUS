package com.ojnexus.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ojnexus.R
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme

@Composable
fun RecoveryBlockedScreen() {
    Column(Modifier.fillMaxSize().padding(NexusSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(NexusSpacing.md)) {
        Text(stringResource(R.string.recovery_blocked_title), style = NexusTheme.typography.title, color = NexusTheme.colors.textPrimary)
        Text(stringResource(R.string.recovery_blocked_preserved), style = NexusTheme.typography.label, color = NexusTheme.colors.textSecondary)
        Text(stringResource(R.string.recovery_blocked_body), style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary)
    }
}

@Composable
fun RecoveryStartingScreen() {
    Column(Modifier.fillMaxSize().padding(NexusSpacing.screenHorizontal)) {
        Text(stringResource(R.string.recovery_starting), style = NexusTheme.typography.label, color = NexusTheme.colors.textSecondary)
    }
}
