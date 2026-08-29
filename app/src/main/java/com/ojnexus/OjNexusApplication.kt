package com.ojnexus

import android.app.Application
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.database.OjNexusDatabase
import java.time.Clock

/**
 * Manual dependency container. Chosen deliberately over Hilt for this phase: the app has a
 * handful of singletons and AGP 9's built-in Kotlin toolchain is new — stability beats
 * framework dogma (see AGENTS.md / docs/ARCHITECTURE.md). Revisit when the graph grows.
 */
class AppContainer(context: android.content.Context) {

    val clock: Clock = Clock.systemDefaultZone()

    val database: OjNexusDatabase = OjNexusDatabase.build(context)

    val problemRepository: ProblemRepository = ProblemRepository(database, clock)
    val reviewRepository: ReviewRepository = ReviewRepository(database, clock)
    val trainingRepository: TrainingRepository = TrainingRepository(database, clock)
    val analyticsRepository: AnalyticsRepository = AnalyticsRepository(database, clock)
}

/**
 * Single-activity app entry point. Compose owns all navigation and theming; this class only
 * provides the dependency container.
 */
class OjNexusApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
