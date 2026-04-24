package dev.fuju.core.storage

/**
 * 直前まで認証済みだったユーザー ID を保持するヒント。
 * `../auth-component/src/store/sessionHint.ts` と同じ目的。
 *
 * モバイル版では Web の `localStorage` は使えないため、プラットフォーム実装で
 * `SharedPreferences` / `NSUserDefaults` を使う。現段階では interface のみ切り、
 * actual 実装は必要になったタイミングで追加する。
 *
 * TODO: フェーズ 4 で Android / iOS 個別に `expect class` を切って実装。
 */
interface SessionHintStore {
    fun read(): String?
    fun write(userId: String)
    fun clear()
}

/** 実運用のデフォルト: in-memory のみ。プロセス再起動で消える。 */
class InMemorySessionHintStore : SessionHintStore {
    private var hint: String? = null
    override fun read(): String? = hint
    override fun write(userId: String) {
        hint = userId
    }
    override fun clear() {
        hint = null
    }
}
