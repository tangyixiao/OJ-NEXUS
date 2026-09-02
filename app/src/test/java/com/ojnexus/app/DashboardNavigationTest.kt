package com.ojnexus.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardNavigationTest {
    @Test
    fun `command deck uses existing destinations`() {
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.TRAINING))
        assertEquals(NexusDestination.TRAINING.route, dashboardCommandRoute(DashboardCommand.REVIEW))
        assertEquals(NexusDestination.PROBLEMS.route, dashboardCommandRoute(DashboardCommand.PROBLEMS))
        assertEquals(NexusRoutes.SUBMISSIONS, dashboardCommandRoute(DashboardCommand.SUBMISSIONS))
    }
}
