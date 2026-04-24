package dev.fuju.core.network

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * プラットフォーム固有の Ktor Engine を返す expect 宣言。
 * - Android: `OkHttp`
 * - iOS (iosX64 / iosArm64 / iosSimulatorArm64): `Darwin`
 *
 * actual は各ターゲットの sourceSet に配置する。
 */
expect fun provideEngineFactory(): HttpClientEngineFactory<*>
