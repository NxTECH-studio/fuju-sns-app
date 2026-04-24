package dev.fuju.feature.timeline.domain

/**
 * Cursor ベースのページング state。React 版 `usePagedList.ts` の `PagedListState<T>`
 * 相当を immutable な value として表現する。
 *
 * ViewModel は [MutableStateFlow] に本 state を載せ、reload / loadMore / refresh /
 * optimistic 更新でコピーを流す。UI 側は `state.items` を描画し、`nextCursor != null`
 * かつ `!loadingMore` のときだけ追加ロードを許可する。
 *
 * @param items 現在までに取得した全ページの結合結果（表示順）
 * @param nextCursor 次ページを取るための opaque トークン。`null` で末端
 * @param loading 初回ロード中フラグ（reload 時に true に戻る）
 * @param loadingMore 追加ロード中フラグ
 * @param error 直近のエラーメッセージ（ユーザ向け）。成功時にクリアされる
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    /** `nextCursor != null` かつ現在ロード中でない場合のみ追加ロード可能。 */
    val canLoadMore: Boolean
        get() = nextCursor != null && !loading && !loadingMore

    /** 初回ロード中で items が空 → spinner のみの画面を出せる。 */
    val isInitialLoading: Boolean
        get() = loading && items.isEmpty()

    /** ロード完了かつ items が空 → EmptyState 分岐に使う。 */
    val isEmpty: Boolean
        get() = !loading && error == null && items.isEmpty()

    companion object {
        /** 未ロードの初期状態。ViewModel の `MutableStateFlow` の seed に使う。 */
        fun <T> initial(): PagedList<T> = PagedList(loading = true)
    }
}
