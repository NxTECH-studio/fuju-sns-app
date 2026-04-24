package dev.fuju.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bearer access token + 期限を保持する store。
 *
 * 本実装は **メモリのみ**。React 版は AuthStore クラスフィールドで保持しており、
 * それと同じ寿命モデル（アプリプロセス起動中のみ）に合わせる。
 *
 * 将来 Android KeyStore / iOS Keychain に永続化する場合は、この interface を実装した
 * PersistentTokenStorage を `expect/actual` で差し替える想定。
 */
interface TokenStorage {
    /** 現在の access token snapshot を購読する。null は未設定。 */
    val tokenFlow: StateFlow<Token?>

    /** 最新トークンを同期で取り出す。interceptor から使う高頻度アクセス用。 */
    fun currentToken(): Token?

    /** 新しいトークンと有効期限（epoch seconds）をセット。 */
    suspend fun setToken(accessToken: String, expiresAtEpochSec: Long)

    /** Token を消去する（logout など）。 */
    suspend fun clear()
}

data class Token(
    val accessToken: String,
    val expiresAtEpochSec: Long,
)

/**
 * Thread-safe in-memory の [TokenStorage] 実装。KMP 全ターゲットで同一実装を使う。
 * Access token は UI / network 両方から読まれるため、[StateFlow] で観測を許す。
 */
class InMemoryTokenStorage : TokenStorage {
    private val state = MutableStateFlow<Token?>(null)
    private val mutex = Mutex()

    override val tokenFlow: StateFlow<Token?> = state.asStateFlow()

    override fun currentToken(): Token? = state.value

    override suspend fun setToken(accessToken: String, expiresAtEpochSec: Long) {
        mutex.withLock {
            state.value = Token(accessToken, expiresAtEpochSec)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            state.value = null
        }
    }
}
