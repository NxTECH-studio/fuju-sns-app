package dev.fuju.composeApp.di

import dev.fuju.composeApp.AppDependencies
import dev.fuju.core.network.FujuHttpClientFactory
import dev.fuju.core.network.FujuNetworkConfig
import dev.fuju.core.network.provideEngineFactory
import dev.fuju.core.storage.InMemorySessionHintStore
import dev.fuju.core.storage.InMemoryTokenStorage
import dev.fuju.feature.admin.data.AdminRepositoryImpl
import dev.fuju.feature.admin.domain.AdminRepository
import dev.fuju.feature.auth.data.AuthRepository
import dev.fuju.feature.auth.domain.AuthConfig
import dev.fuju.feature.auth.domain.AuthStateMachine
import dev.fuju.feature.profile.data.ProfileRepositoryImpl
import dev.fuju.feature.profile.domain.ProfileRepository
import dev.fuju.feature.timeline.data.TimelineRepositoryImpl
import dev.fuju.feature.timeline.domain.TimelineRepository

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

    val authRepository = AuthRepository(authHttpClient, tokenStorage, sessionHint)
    val authStateMachine = AuthStateMachine(authRepository, AuthConfig())

    val timelineRepository: TimelineRepository = TimelineRepositoryImpl(apiHttpClient)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(apiHttpClient)
    val adminRepository: AdminRepository = AdminRepositoryImpl(apiHttpClient)

    fun asAppDependencies(): AppDependencies =
        AppDependencies(
            authStateMachine = authStateMachine,
            timelineRepository = timelineRepository,
            profileRepository = profileRepository,
            adminRepository = adminRepository,
        )

    override fun close() {
        authStateMachine.dispose()
        authHttpClient.close()
        apiHttpClient.close()
    }
}
