package com.ojnexus

import android.app.Application
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.DemoDataSeeder
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.database.OjNexusDatabase
import com.ojnexus.core.network.CoroutineDelayProvider
import com.ojnexus.core.network.SystemMonotonicClock
import com.ojnexus.judge.codeforces.CodeforcesClient
import com.ojnexus.judge.codeforces.CodeforcesRequestGate
import com.ojnexus.judge.codeforces.CodeforcesSyncCoordinator
import com.ojnexus.judge.codeforces.CodeforcesSyncRepository
import com.ojnexus.judge.codeforces.RetrofitCodeforcesAdapter
import com.ojnexus.judge.codeforces.SyncPolicy
import com.ojnexus.judge.codeforces.api.CodeforcesApi
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    // --- Codeforces adapter stack (single request gate for the whole process) ---

    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val codeforcesApi: CodeforcesApi = Retrofit.Builder()
        .baseUrl(com.ojnexus.judge.codeforces.CodeforcesUrls.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CodeforcesApi::class.java)
    private val requestGate = CodeforcesRequestGate(
        minimumIntervalMs = SyncPolicy.REQUEST_INTERVAL_MS,
        clock = SystemMonotonicClock(),
        delayProvider = CoroutineDelayProvider(),
    )
    private val codeforcesClient = CodeforcesClient(codeforcesApi, requestGate)
    private val codeforcesAdapter: RetrofitCodeforcesAdapter = RetrofitCodeforcesAdapter(codeforcesClient)

    val judgeAccountRepository: JudgeAccountRepository =
        JudgeAccountRepository(database, codeforcesAdapter, clock)

    val codeforcesSyncRepository: CodeforcesSyncRepository =
        CodeforcesSyncRepository(database, codeforcesAdapter, clock)

    val syncCoordinator: CodeforcesSyncCoordinator =
        CodeforcesSyncCoordinator(judgeAccountRepository, codeforcesSyncRepository)

    val demoSeeder: DemoDataSeeder = DemoDataSeeder(
        database = database,
        problemRepository = problemRepository,
        reviewRepository = reviewRepository,
        trainingRepository = trainingRepository,
        clock = clock,
    )
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
