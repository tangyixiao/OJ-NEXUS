package com.ojnexus.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.feature.analytics.AnalyticsScreen
import com.ojnexus.feature.dashboard.DashboardScreen
import com.ojnexus.feature.profile.ProfileScreen
import com.ojnexus.feature.problems.ProblemsScreen
import com.ojnexus.feature.training.TrainingScreen

/**
 * Application shell: dark background, top-level NavHost and the flat bottom bar.
 * Status bar inset is consumed once here; screens lay out below it.
 */
@Composable
fun NexusApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            NavHost(
                navController = navController,
                startDestination = NexusDestination.DASHBOARD.route,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    fadeIn(
                        tween(
                            NexusMotion.DURATION_NORMAL,
                            easing = NexusMotion.EasingStandard,
                        ),
                    )
                },
                exitTransition = {
                    fadeOut(
                        tween(
                            NexusMotion.DURATION_NORMAL,
                            easing = NexusMotion.EasingExit,
                        ),
                    )
                },
                popEnterTransition = {
                    fadeIn(
                        tween(
                            NexusMotion.DURATION_NORMAL,
                            easing = NexusMotion.EasingStandard,
                        ),
                    )
                },
                popExitTransition = {
                    fadeOut(
                        tween(
                            NexusMotion.DURATION_NORMAL,
                            easing = NexusMotion.EasingExit,
                        ),
                    )
                },
            ) {
                composable(NexusDestination.DASHBOARD.route) { DashboardScreen() }
                composable(NexusDestination.PROBLEMS.route) { ProblemsScreen() }
                composable(NexusDestination.TRAINING.route) { TrainingScreen() }
                composable(NexusDestination.ANALYTICS.route) { AnalyticsScreen() }
                composable(NexusDestination.PROFILE.route) { ProfileScreen() }
            }

            NexusBottomBar(
                destinations = NexusDestination.entries.toList(),
                currentRoute = currentRoute,
                onSelect = { destination -> navController.navigateToTopLevel(destination.route) },
            )
        }
    }
}

private fun NavController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
