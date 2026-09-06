package com.ojnexus.app

import com.ojnexus.feature.dashboard.DashboardAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardNavigationTest {
    @Test
    fun `command deck uses existing destinations`() {
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.TRAINING))
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.REVIEW))
        assertEquals(NexusDestination.PROBLEMS.route, dashboardCommandRoute(DashboardCommand.PROBLEMS))
        assertEquals(NexusRoutes.SUBMISSIONS, dashboardCommandRoute(DashboardCommand.SUBMISSIONS))
    }

    @Test
    fun `dashboard actions preserve stable identifiers in routes`() {
        assertEquals("session/9", dashboardActionRoute(DashboardAction.ResumeSession(9L)))
        assertEquals("problem/11", dashboardActionRoute(DashboardAction.OpenTask(3L, 11L)))
        assertEquals("review/12", dashboardActionRoute(DashboardAction.OpenReview(12L)))
        assertEquals("submissions", dashboardActionRoute(DashboardAction.OpenSubmission("req-1")))
        assertEquals(
            "contest-focus/Codeforces/round%2F1",
            dashboardActionRoute(DashboardAction.OpenContest("Codeforces", "round/1")),
        )
        assertNull(dashboardActionRoute(DashboardAction.NoAction))
    }
}
