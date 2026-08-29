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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ojnexus.OjNexusApplication
import com.ojnexus.core.designsystem.NexusMotion
import com.ojnexus.core.designsystem.NexusTheme
import com.ojnexus.core.ui.LocalAppContainer
import com.ojnexus.feature.analytics.AnalyticsScreen
import com.ojnexus.feature.contests.ContestCenterScreen
import com.ojnexus.feature.dashboard.DashboardScreen
import com.ojnexus.feature.profile.ProfileScreen
import com.ojnexus.feature.problems.ProblemDetailScreen
import com.ojnexus.feature.problems.ProblemFormScreen
import com.ojnexus.feature.problems.ProblemsScreen
import com.ojnexus.feature.settings.SettingsScreen
import com.ojnexus.feature.training.TrainingScreen

/** Route constants. Only stable IDs travel through routes; screens load the rest. */
object NexusRoutes {
    const val PROBLEM_DETAIL = "problem/{problemId}"
    const val PROBLEM_ADD = "problem/add"
    const val PROBLEM_EDIT = "problem/{problemId}/edit"
    const val REVIEW_SESSION = "review/{problemId}"
    const val SESSION_ACTIVE = "session/active"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val CONTESTS = "contests"
    const val SETTINGS = "settings"
}

private val fadeEnter = fadeIn(tween(NexusMotion.DURATION_NORMAL, easing = NexusMotion.EasingStandard))
private val fadeExit = fadeOut(tween(NexusMotion.DURATION_NORMAL, easing = NexusMotion.EasingExit))

/**
 * Application shell: dark background, top-level NavHost and the flat bottom bar.
 * Status bar inset is consumed once here; screens lay out below it.
 */
@Composable
fun NexusApp(modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as OjNexusApplication).container
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(LocalAppContainer provides container) {
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
                    enterTransition = { fadeEnter },
                    exitTransition = { fadeExit },
                    popEnterTransition = { fadeEnter },
                    popExitTransition = { fadeExit },
                ) {
                    composable(NexusDestination.DASHBOARD.route) {
                        DashboardScreen(
                            onOpenContests = { navController.navigate(NexusRoutes.CONTESTS) },
                            onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) },
                        )
                    }
                    composable(NexusDestination.PROBLEMS.route) {
                        ProblemsScreen(
                            onOpenProblem = { id -> navController.navigate("problem/$id") },
                            onAddProblem = { navController.navigate(NexusRoutes.PROBLEM_ADD) },
                        )
                    }
                    composable(NexusDestination.TRAINING.route) {
                        TrainingScreen(
                            onOpenSession = { id ->
                                navController.navigate(
                                    if (id == null) NexusRoutes.SESSION_ACTIVE else "session/$id",
                                )
                            },
                            onOpenReview = { id -> navController.navigate("review/$id") },
                        )
                    }
                    composable(NexusDestination.ANALYTICS.route) { AnalyticsScreen() }
                    composable(NexusDestination.PROFILE.route) {
                        ProfileScreen(onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) })
                    }

                    composable(route = NexusRoutes.CONTESTS) {
                        ContestCenterScreen(onBack = { navController.popBackStack() })
                    }
                    composable(route = NexusRoutes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }

                    composable(
                        route = NexusRoutes.PROBLEM_DETAIL,
                        arguments = listOf(navArgument("problemId") { type = NavType.LongType }),
                    ) { entry ->
                        val problemId = entry.arguments?.getLong("problemId") ?: return@composable
                        ProblemDetailScreen(
                            problemId = problemId,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate("problem/$id/edit") },
                            onOpenReview = { id -> navController.navigate("review/$id") },
                        )
                    }
                    composable(route = NexusRoutes.PROBLEM_ADD) {
                        ProblemFormScreen(editProblemId = null, onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = NexusRoutes.PROBLEM_EDIT,
                        arguments = listOf(navArgument("problemId") { type = NavType.LongType }),
                    ) { entry ->
                        val problemId = entry.arguments?.getLong("problemId") ?: return@composable
                        ProblemFormScreen(editProblemId = problemId, onDone = { navController.popBackStack() })
                    }
                    composable(
                        route = NexusRoutes.REVIEW_SESSION,
                        arguments = listOf(navArgument("problemId") { type = NavType.LongType }),
                    ) { entry ->
                        val problemId = entry.arguments?.getLong("problemId") ?: return@composable
                        com.ojnexus.feature.training.ReviewSessionScreen(
                            problemId = problemId,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable(route = NexusRoutes.SESSION_ACTIVE) {
                        com.ojnexus.feature.training.SessionScreen(
                            sessionId = null,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = NexusRoutes.SESSION_DETAIL,
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                    ) { entry ->
                        val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
                        com.ojnexus.feature.training.SessionScreen(
                            sessionId = sessionId,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }

                NexusBottomBar(
                    destinations = NexusDestination.entries.toList(),
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        if (destination.route != currentRoute) {
                            navController.navigateToTopLevel(destination.route)
                        }
                    },
                )
            }
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
