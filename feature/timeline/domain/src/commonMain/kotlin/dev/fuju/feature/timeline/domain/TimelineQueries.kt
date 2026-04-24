package dev.fuju.feature.timeline.domain

import dev.fuju.core.domain.Post

/**
 * Backend `GET /timeline/{home,global,user}` / `GET /posts/{id}/replies` で使う
 * ページング用パラメータ。React 版 `usePagedList.ts` と同じく cursor ベース。
 *
 * - `cursor == null` で先頭ページを取得
 * - サーバは `PostListResponse { data, next_cursor }` を返し、最終ページでは
 *   `next_cursor == null` となる
 */
data class TimelineQuery(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT: Int = 20
    }
}

/**
 * `PostListResponse` の domain 側表現。Repository → ViewModel への境界で使う。
 * React の `Page<T>` (`usePagedList.ts`) と同型。
 */
data class PostPage(
    val items: List<Post>,
    val nextCursor: String?,
)

/**
 * Timeline の種類。[TimelineRepository] のどのエンドポイントを叩くかを決める。
 * React 版の `TimelineKind` と同じ三値。
 */
sealed interface TimelineKind {
    data object Home : TimelineKind

    data object Global : TimelineKind

    data class User(
        val sub: String,
    ) : TimelineKind
}

/**
 * Timeline 取得の抽象。data 層で Ktor 経由に差し替える。
 *
 * ページングは [PostPage.nextCursor] を呼び出し側で [TimelineQuery.cursor] に載せて
 * 返せば良い。呼び出し側は [TimelineViewModel] が基本。
 */
interface TimelineRepository {
    suspend fun getHome(query: TimelineQuery): PostPage

    suspend fun getGlobal(query: TimelineQuery): PostPage

    suspend fun getUser(
        sub: String,
        query: TimelineQuery,
    ): PostPage

    suspend fun getPost(id: String): Post

    suspend fun getReplies(
        id: String,
        query: TimelineQuery,
    ): PostPage

    suspend fun likePost(id: String)

    suspend fun unlikePost(id: String)

    suspend fun createPost(
        content: String,
        imageIds: List<String>,
        parentPostId: String?,
    ): Post

    suspend fun deletePost(id: String)
}
