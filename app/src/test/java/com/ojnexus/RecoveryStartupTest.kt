package com.ojnexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ojnexus.app.NexusStartupState
import com.ojnexus.core.data.restore.RecoveryDisposition
import com.ojnexus.core.data.restore.RestoreFailure
import com.ojnexus.core.data.restore.RestoreReadiness
import com.ojnexus.core.database.OjNexusDatabase
import org.junit.Assert.*
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = StartupProbeApplication::class)
class RecoveryStartupTest {
    @Test fun startupStateStartsWithoutConstructingGraph() {
        val application = StartupProbeApplication()
        assertSame(NexusStartupState.Starting, application.startupState)
        assertFalse(application.graphCreated)
    }

    @Test fun blockedReadinessDoesNotConstructGraphBootstrapOrOpenMissingOjDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = ApplicationProvider.getApplicationContext<StartupProbeApplication>()
        assertTrue(application.startupState is NexusStartupState.Blocked)
        assertFalse(application.graphCreated)
        assertFalse(application.bootstrapCalled)
        assertFalse(context.getDatabasePath(OjNexusDatabase.DATABASE_NAME).exists())
    }
}

class StartupProbeApplication : OjNexusApplication() {
    var graphCreated = false
    var bootstrapCalled = false
    override fun attachBaseContext(base: Context) {
        base.getDatabasePath(OjNexusDatabase.DATABASE_NAME).delete()
        File(base.filesDir, "oj-nexus-restore.journal").writeText(
            "stage=ROLLED_BACK\ngeneration=old-generation\ndetail=ROLLBACK_FAILURE\n",
        )
        super.attachBaseContext(base)
    }
    override fun createAppContainer(readiness: RestoreReadiness.Ready): AppContainer {
        graphCreated = true
        error("blocked startup must not create the graph")
    }
    override fun bootstrapReadyContainer(container: AppContainer) { bootstrapCalled = true }
    override fun initializeGlobalContext() = Unit
    override fun installCrashLogIfOptedIn(container: AppContainer) = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = CleanInstallProbeApplication::class)
class RecoveryCleanInstallStartupTest {
    @Test fun cleanInstallResolvesReadyAndCreatesNormalGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ApplicationProvider.getApplicationContext<CleanInstallProbeApplication>()
        assertTrue(app.startupState is NexusStartupState.Ready)
        app.container.database.openHelper.writableDatabase
        assertTrue(context.getDatabasePath(OjNexusDatabase.DATABASE_NAME).exists())
    }
}

class CleanInstallProbeApplication : OjNexusApplication() {
    override fun bootstrapReadyContainer(container: AppContainer) = Unit
    override fun initializeGlobalContext() = Unit
    override fun installCrashLogIfOptedIn(container: AppContainer) = Unit
}
