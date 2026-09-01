package com.ojnexus.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ojnexus.feature.contests.ContestFocusScreen
import com.ojnexus.feature.dashboard.DashboardScreen
import com.ojnexus.feature.profile.ProfileScreen
import com.ojnexus.feature.problems.ProblemDetailScreen
import com.ojnexus.feature.problems.ProblemFormScreen
import com.ojnexus.feature.problems.LuoguProblemDetailScreen
import com.ojnexus.feature.problems.ProblemsScreen
import com.ojnexus.feature.settings.SettingsScreen
import com.ojnexus.feature.submissions.SubmissionCenterScreen
import com.ojnexus.feature.training.TrainingScreen
import com.ojnexus.feature.workspace.WorkspaceScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Route constants. Only stable IDs travel through routes; screens load the rest. */
object NexusRoutes {
    const val PROBLEM_DETAIL = "problem/{problemId}"
    const val PROBLEM_ADD = "problem/add"
    const val PROBLEM_EDIT = "problem/{problemId}/edit"
    const val REVIEW_SESSION = "review/{problemId}"
    const val SESSION_ACTIVE = "session/active"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val CONTESTS = "contests"
    const val CONTEST_FOCUS = "contest-focus/{judge}/{contestId}"
    const val SETTINGS = "settings"
    const val SETTINGS_OPENAPP = "settings/openapp"
    const val SETTINGS_LUOGU = "settings/luogu"
    const val SUBMISSIONS = "submissions"
    const val WORKSPACE = "workspace/{pid}?title={title}"
    const val LUOGU_PROBLEM_DETAIL = "luogu-problem/{pid}"

    fun workspace(pid: String, title: String? = null): String {
        val base = "workspace/${encodeRouteValue(pid)}"
        val normalizedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        return normalizedTitle?.let {
            "$base?title=${encodeRouteValue(it)}"
        } ?: base
    }

    private fun encodeRouteValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}

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
    var commandPaletteOpen by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = NexusTheme.reduceMotion
    val enterTransition = remember(reduceMotion) {
        if (reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(NexusMotion.DURATION_NORMAL, easing = NexusMotion.EasingStandard))
        }
    }
    val exitTransition = remember(reduceMotion) {
        if (reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(NexusMotion.DURATION_NORMAL, easing = NexusMotion.EasingExit))
        }
    }

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
                    enterTransition = { enterTransition },
                    exitTransition = { exitTransition },
                    popEnterTransition = { enterTransition },
                    popExitTransition = { exitTransition },
                ) {
                    composable(NexusDestination.DASHBOARD.route) {
                        DashboardScreen(
                            onOpenContests = { navController.navigate(NexusRoutes.CONTESTS) },
                            onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) },
                            onOpenLuoguSetup = { navController.navigate(NexusRoutes.SETTINGS_LUOGU) },
                        )
                    }
                    composable(NexusDestination.PROBLEMS.route) {
                        ProblemsScreen(
                            onOpenProblem = { id -> navController.navigate("problem/$id") },
                            onAddProblem = { navController.navigate(NexusRoutes.PROBLEM_ADD) },
                            onOpenWorkspace = { pid ->
                                navController.navigate(NexusRoutes.workspace(pid))
                            },
                            onOpenLuoguDetail = { pid ->
                                navController.navigate("luogu-problem/${android.net.Uri.encode(pid)}")
                            },
                        )
                    }
                    composable(NexusDestination.TRAINING.route) {
                        TrainingScreen(
                            onOpenProblem = { id -> navController.navigate("problem/$id") },
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
                        ProfileScreen(
                            onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) },
                            onOpenSubmissions = { navController.navigate(NexusRoutes.SUBMISSIONS) },
                        )
                    }

                    composable(route = NexusRoutes.CONTESTS) {
                        ContestCenterScreen(
                            onBack = { navController.popBackStack() },
                            onOpenFocus = { judge, contestId ->
                                navController.navigate("contest-focus/${android.net.Uri.encode(judge)}/${android.net.Uri.encode(contestId)}")
                            },
                        )
                    }
                    composable(
                        route = NexusRoutes.CONTEST_FOCUS,
                        arguments = listOf(
                            navArgument("judge") { type = NavType.StringType },
                            navArgument("contestId") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val judge = entry.arguments?.getString("judge") ?: return@composable
                        val contestId = entry.arguments?.getString("contestId") ?: return@composable
                        ContestFocusScreen(
                            judge = judge,
                            contestId = contestId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(route = NexusRoutes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(route = NexusRoutes.SETTINGS_OPENAPP) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            focusOpenApp = true,
                        )
                    }
                    composable(route = NexusRoutes.SETTINGS_LUOGU) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            focusLuogu = true,
                        )
                    }
                    composable(route = NexusRoutes.SUBMISSIONS) {
                        SubmissionCenterScreen(
                            onBack = { navController.popBackStack() },
                            onOpenWorkspace = { pid ->
                                navController.navigate(NexusRoutes.workspace(pid))
                            },
                        )
                    }
                    composable(
                        route = NexusRoutes.WORKSPACE,
                        arguments = listOf(
                            navArgument("pid") { type = NavType.StringType },
                            navArgument("title") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        val pid = entry.arguments?.getString("pid") ?: return@composable
                        val title = entry.arguments?.getString("title")
                        WorkspaceScreen(
                            pid = pid,
                            title = title,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS_OPENAPP) },
                        )
                    }
                    composable(
                        route = NexusRoutes.LUOGU_PROBLEM_DETAIL,
                        arguments = listOf(navArgument("pid") { type = NavType.StringType }),
                    ) { entry ->
                        val pid = entry.arguments?.getString("pid") ?: return@composable
                        LuoguProblemDetailScreen(
                            pid = pid,
                            onBack = { navController.popBackStack() },
                            onOpenWorkspace = { problemPid, title ->
                                navController.navigate(NexusRoutes.workspace(problemPid, title))
                            },
                        )
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
                            onOpenWorkspace = { pid -> navController.navigate(NexusRoutes.workspace(pid)) },
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
                    onOpenCommandPalette = { commandPaletteOpen = true },
                )
            }
        }
        if (commandPaletteOpen) {
            CommandPalette(
                onDismiss = { commandPaletteOpen = false },
                onExecute = { command ->
                    commandPaletteOpen = false
                    when (command) {
                        "dashboard", "problems", "training", "analytics", "profile" ->
                            navController.navigateToTopLevel(command)
                        "contests" -> navController.navigate(NexusRoutes.CONTESTS)
                        "submissions" -> navController.navigate(NexusRoutes.SUBMISSIONS)
                        "add_problem" -> navController.navigate(NexusRoutes.PROBLEM_ADD)
                        "settings" -> navController.navigate(NexusRoutes.SETTINGS)
                    }
                },
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
