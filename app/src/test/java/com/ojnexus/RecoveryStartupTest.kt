package com.ojnexus

import android.content.Context
import android.content.SharedPreferences
import android.content.ContentResolver
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
        val database = context.getDatabasePath(OjNexusDatabase.DATABASE_NAME)
        assertTrue(application.startupState is NexusStartupState.Blocked)
        assertFalse(application.graphCreated)
        assertFalse(application.bootstrapCalled)
        assertEquals(0, application.globalContextCalls)
        assertEquals(0, application.crashHookCalls)
        assertFalse(database.exists())
    }
}

open class StartupProbeApplication : OjNexusApplication() {
    var graphCreated = false
    var bootstrapCalled = false
    var globalContextCalls = 0
    var crashHookCalls = 0
    var businessIoCalls = 0

    fun resetBusinessIoCalls() { businessIoCalls = 0 }
    private fun countIo() { businessIoCalls++ }

    override fun getDatabasePath(name: String): File { countIo(); return super.getDatabasePath(name) }
    override fun getFilesDir(): File { countIo(); return super.getFilesDir() }
    override fun getCacheDir(): File { countIo(); return super.getCacheDir() }
    override fun getNoBackupFilesDir(): File { countIo(); return super.getNoBackupFilesDir() }
    override fun getFileStreamPath(name: String): File { countIo(); return super.getFileStreamPath(name) }
    override fun getDir(name: String, mode: Int): File { countIo(); return super.getDir(name, mode) }
    override fun openFileInput(name: String): java.io.FileInputStream { countIo(); return super.openFileInput(name) }
    override fun openFileOutput(name: String, mode: Int): java.io.FileOutputStream { countIo(); return super.openFileOutput(name, mode) }
    override fun deleteFile(name: String): Boolean { countIo(); return super.deleteFile(name) }
    override fun getContentResolver(): ContentResolver { countIo(); return super.getContentResolver() }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        countIo()
        return super.getSharedPreferences(name, mode)
    }

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
    override fun bootstrapReadyContainer(container: AppContainer) { bootstrapCalled = true; error("blocked startup must not bootstrap") }
    override fun initializeGlobalContext() { globalContextCalls++; error("blocked startup must not initialize global context") }
    override fun installCrashLogIfOptedIn(container: AppContainer) { crashHookCalls++; error("blocked startup must not read crash preferences") }
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
