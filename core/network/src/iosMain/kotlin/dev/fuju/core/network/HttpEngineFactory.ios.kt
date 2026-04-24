package dev.fuju.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun provideEngineFactory(): HttpClientEngineFactory<*> = Darwin
