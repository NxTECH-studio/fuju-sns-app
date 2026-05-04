package dev.fuju.composeApp.di

import dev.fuju.composeApp.AppDependencies
import dev.fuju.core.network.FujuHttpClientFactory
import dev.fuju.core.network.FujuNetworkConfig
import dev.fuju.core.network.provideEngineFactory
import dev.fuju.core.storage.InMemorySessionHintStore
import dev.fuju.core.storage.InMemoryTokenStorage
import dev.fuju.core.telemetry.TelemetryDispatcher
import dev.fuju.core.telemetry.TelemetryHttpClient
import dev.fuju.feature.auth.data.AuthRepository
import dev.fuju.feature.auth.domain.AuthConfig
import dev.fuju.feature.auth.domain.AuthStateMachine
import dev.fuju.feature.profile.data.ProfileRepositoryImpl
import dev.fuju.feature.profile.domain.ProfileRepository
import dev.fuju.feature.timeline.data.TimelineRepositoryImpl
import dev.fuju.feature.timeline.domain.TimelineRepository
import kotlinx.coroutines.runBlocking

/**
 * 軽量な手書き DI コンテナ。プラットフォームごとの差異（KeyStore 等）は今のところ
 * 無いため、設定値 (baseURL) のみ外部から注入する。
 *
 * Activity / ViewController のライフサイクル終了時に [close] を呼び、
 * Ktor HttpClient のコネクションプール / coroutine scope をクリーンアップする。
 */
class AppContainer(
    authCoreBaseUrl: String,
    fujuApiBaseUrl: String,
    fujuModelBaseUrl: String,
    fujuModelTenantId: String,
    verboseLogging: Boolean = false,
) : AutoCloseable {
    private val tokenStorage = InMemoryTokenStorage()
    private val sessionHint = InMemorySessionHintStore()
    private val factory =
        FujuHttpClientFactory(
            tokenStorage = tokenStorage,
            engineFactory = provideEngineFactory(),
            config = FujuNetworkConfig(verboseLogging = verboseLogging),
        )

    val authHttpClient = factory.create(authCoreBaseUrl, enableBearer = true)
    val apiHttpClient = factory.create(fujuApiBaseUrl, enableBearer = true)
    val modelHttpClient = factory.create(fujuModelBaseUrl, enableBearer = true)

    val authRepository = AuthRepository(authHttpClient, tokenStorage, sessionHint)
    val authStateMachine = AuthStateMachine(authRepository, AuthConfig())

    val timelineRepository: TimelineRepository = TimelineRepositoryImpl(apiHttpClient)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(apiHttpClient)

    // Telemetry direct to fuju-emotion-model. The wire payload no
    // longer carries user_id — the model derives it from the AuthCore
    // Bearer's `sub` claim server-side. Reading auth state lazily lets
    // sign-in transitions take effect on the next flush without
    // recreating the dispatcher; we just gate flushes on whether a
    // user is signed in (otherwise the model 401s anonymous POSTs).
    val telemetryDispatcher: TelemetryDispatcher =
        TelemetryDispatcher(
            sender =
                TelemetryHttpClient(
                    client = modelHttpClient,
                    tenantId = fujuModelTenantId,
                    signedInProvider = {
                        authStateMachine.state.value.user != null
                    },
                ),
        )

    fun asAppDependencies(): AppDependencies =
        AppDependencies(
            authStateMachine = authStateMachine,
            timelineRepository = timelineRepository,
            profileRepository = profileRepository,
            telemetryDispatcher = telemetryDispatcher,
        )

    override fun close() {
        authStateMachine.dispose()
        // Final-flush + cancel the dispatcher coroutine before tearing
        // down the http client. runBlocking here is acceptable: this
        // path runs once on Activity / ViewController teardown and
        // already blocks on httpClient.close().
        runBlocking { telemetryDispatcher.shutdown() }
        authHttpClient.close()
        apiHttpClient.close()
        modelHttpClient.close()
    }
}
