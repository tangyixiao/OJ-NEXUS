package com.ojnexus

import android.app.Application
import com.ojnexus.core.data.repository.AnalyticsRepository
import com.ojnexus.core.data.repository.BackupRepository
import com.ojnexus.core.data.repository.ContestFocusRepository
import com.ojnexus.core.data.repository.DemoDataSeeder
import com.ojnexus.core.data.repository.JudgeAccountRepository
import com.ojnexus.core.data.repository.JudgeDataRepository
import com.ojnexus.core.data.repository.KnowledgeRepository
import com.ojnexus.core.data.repository.ProblemRepository
import com.ojnexus.core.data.repository.ReviewRepository
import com.ojnexus.core.data.repository.TrainingRepository
import com.ojnexus.core.data.preferences.UserPreferencesRepository
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
import com.ojnexus.core.network.RateLimitedRequestGate
import com.ojnexus.judge.JudgeRegistry
import com.ojnexus.judge.JudgeSyncDispatcher
import com.ojnexus.judge.atcoder.AtCoderAccountConnector
import com.ojnexus.judge.atcoder.AtCoderProblemsClient
import com.ojnexus.judge.atcoder.AtCoderSyncCoordinator
import com.ojnexus.judge.atcoder.AtCoderSyncPolicy
import com.ojnexus.judge.atcoder.AtCoderSyncRepository
import com.ojnexus.judge.atcoder.AtCoderUrls
import com.ojnexus.judge.atcoder.RetrofitAtCoderAdapter
import com.ojnexus.judge.atcoder.api.AtCoderProblemsApi
import com.ojnexus.judge.codeforces.CodeforcesAccountConnector
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

    init {
        BackupRepository.restorePending(context)
    }

    val clock: Clock = Clock.systemDefaultZone()

    val database: OjNexusDatabase = OjNexusDatabase.build(context)

    val problemRepository: ProblemRepository = ProblemRepository(database, clock)
    val reviewRepository: ReviewRepository = ReviewRepository(database, clock)
    val trainingRepository: TrainingRepository = TrainingRepository(database, clock)
    val analyticsRepository: AnalyticsRepository = AnalyticsRepository(database, clock)
    val contestFocusRepository: ContestFocusRepository = ContestFocusRepository(database, clock)
    val knowledgeRepository: KnowledgeRepository = KnowledgeRepository(database)
    val backupRepository: BackupRepository = BackupRepository(database, context)
    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)
    val judgeDataRepository = JudgeDataRepository(database)

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
    private val codeforcesGate = CodeforcesRequestGate(
        minimumIntervalMs = SyncPolicy.REQUEST_INTERVAL_MS,
        clock = SystemMonotonicClock(),
        delayProvider = CoroutineDelayProvider(),
    )
    private val codeforcesClient = CodeforcesClient(codeforcesApi, codeforcesGate)
    private val codeforcesAdapter: RetrofitCodeforcesAdapter = RetrofitCodeforcesAdapter(codeforcesClient)

    // --- AtCoder Problems adapter stack (independent community-source request gate) ---

    private val atCoderApi: AtCoderProblemsApi = Retrofit.Builder()
        .baseUrl(AtCoderUrls.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AtCoderProblemsApi::class.java)
    private val atCoderGate = RateLimitedRequestGate(
        minimumIntervalMs = AtCoderSyncPolicy.REQUEST_INTERVAL_MS,
        clock = SystemMonotonicClock(),
        delayProvider = CoroutineDelayProvider(),
    )
    private val atCoderClient = AtCoderProblemsClient(atCoderApi, atCoderGate)
    private val atCoderAdapter = RetrofitAtCoderAdapter(atCoderClient)

    val judgeRegistry = JudgeRegistry(
        adapters = listOf(codeforcesAdapter, atCoderAdapter),
        accountConnectors = listOf(
            CodeforcesAccountConnector(codeforcesAdapter),
            AtCoderAccountConnector(atCoderAdapter),
        ),
    )

    val judgeAccountRepository: JudgeAccountRepository =
        JudgeAccountRepository(database, judgeRegistry, clock)

    val codeforcesSyncRepository: CodeforcesSyncRepository =
        CodeforcesSyncRepository(database, codeforcesAdapter, clock)

    val syncCoordinator: CodeforcesSyncCoordinator =
        CodeforcesSyncCoordinator(judgeAccountRepository, codeforcesSyncRepository)

    val atCoderSyncRepository = AtCoderSyncRepository(database, atCoderAdapter, clock)
    val atCoderSyncCoordinator = AtCoderSyncCoordinator(judgeAccountRepository, atCoderSyncRepository)

    init {
        judgeRegistry.attachSyncCoordinators(listOf(syncCoordinator, atCoderSyncCoordinator))
    }

    val syncDispatcher = JudgeSyncDispatcher(judgeAccountRepository, judgeRegistry)

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
        com.ojnexus.core.ui.GlobalContext.init(this)
    }
}
