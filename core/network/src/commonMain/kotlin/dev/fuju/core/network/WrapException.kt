package dev.fuju.core.network

import dev.fuju.core.error.AuthException
import dev.fuju.core.error.toAuthException
import kotlinx.coroutines.CancellationException

/**
 * Repository 実装で共通に使う例外変換ラッパー。
 *
 * - `CancellationException` は coroutine キャンセル契約を守るため素通し。
 * - `AuthException` はそのまま再 throw（追加情報を持っているため wrap しない）。
 * - その他の `Throwable` は `toAuthException()` で `AuthException` に正規化する。
 *
 * `suspend` 関数内で HTTP 呼び出しをまとめるときに使う想定。inline なので
 * サスペンド呼び出しをブロック内に書いても効率に影響しない。
 */
inline fun <T> wrapAsAuthException(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (t: Throwable) {
        throw t.toAuthException()
    }
