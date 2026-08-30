package com.ojnexus.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.ojnexus.core.designsystem.NexusSize
import com.ojnexus.core.designsystem.NexusSpacing
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.designsystem.component.NexusDivider

/**
 * Flat bottom navigation: hairline top edge, icon + uppercase label per tab, a short accent
 * indicator over the selected tab. Labels carry the accessibility text; icons are decorative.
 */
@Composable
fun NexusBottomBar(
    destinations: List<NexusDestination>,
    currentRoute: String?,
    onSelect: (NexusDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NexusTheme.colors
    val hapticsEnabled = NexusTheme.hapticsEnabled
    val hapticFeedback = LocalHapticFeedback.current
    Column(modifier = modifier.background(colors.surface)) {
        NexusDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(NexusSize.bottomBarHeight),
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == currentRoute
                val tint = if (selected) colors.accent else colors.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (hapticsEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSelect(destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(NexusSize.bottomBarIndicatorWidth)
                            .height(NexusSize.bottomBarIndicatorHeight)
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                    Spacer(modifier = Modifier.height(NexusSpacing.xxs))
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(NexusSize.navIcon),
                    )
                    Spacer(modifier = Modifier.height(NexusSpacing.xxxs))
                    Text(
                        text = stringResource(destination.labelRes),
                        style = NexusTheme.typography.sectionLabel,
                        color = tint,
                    )
                }
            }
        }
    }
}
