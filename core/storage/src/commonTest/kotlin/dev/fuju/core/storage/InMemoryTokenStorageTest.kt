package dev.fuju.core.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryTokenStorageTest {
    @Test
    fun setAndGet() =
        runTest {
            val store = InMemoryTokenStorage()
            assertNull(store.currentToken())
            store.setToken("abc", 1_800_000_000L)
            assertEquals("abc", store.currentToken()?.accessToken)
            assertEquals(1_800_000_000L, store.currentToken()?.expiresAtEpochSec)
        }

    @Test
    fun clear() =
        runTest {
            val store = InMemoryTokenStorage()
            store.setToken("abc", 1L)
            store.clear()
            assertNull(store.currentToken())
        }
}
