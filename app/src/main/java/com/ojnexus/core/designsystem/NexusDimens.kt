package com.ojnexus.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * OJ NEXUS spacing tokens. Corners stay restrained: 4–8dp for small controls,
 * 8–12dp for containers. Nothing larger — hierarchy comes from lines and type, not rounding.
 */
object NexusSpacing {
    val xxxs = 2.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Default horizontal content padding for screens. */
    val screenHorizontal = md
}

/** Corner radius tokens. */
object NexusRadius {
    val xs = RoundedCornerShape(4.dp)
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(12.dp)
}

/** Fixed heights used across the shell so rhythm stays consistent. */
object NexusSize {
    val topBarHeight = 48.dp
    val bottomBarHeight = 60.dp
    val commandBarHeight = 36.dp
    val commandPaletteMaxHeight = 360.dp
    val bottomBarIndicatorWidth = 16.dp
    val bottomBarIndicatorHeight = 2.dp
    val navIcon = 20.dp
    val tableRowHeight = 44.dp
    val dividerThickness = 1.dp
    val statusDot = 6.dp
}
