package dev.fuju.feature.timeline.domain

import dev.fuju.core.domain.Post

/**
 * Backend `GET /timeline/{home,global,user}` で使うパラメータ。React 版の offset ページング。
 */
data class TimelineQuery(
    val limit: Int = 20,
    val offset: Int = 0,
)

/**
 * Timeline 取得の抽象。data 層で Ktor 経由に差し替える。
 */
interface TimelineRepository {
    suspend fun getHome(query: TimelineQuery): List<Post>
    suspend fun getGlobal(query: TimelineQuery): List<Post>
    suspend fun getUser(sub: String, query: TimelineQuery): List<Post>
    suspend fun getPost(id: String): Post
    suspend fun getReplies(id: String, query: TimelineQuery): List<Post>
    suspend fun likePost(id: String)
    suspend fun unlikePost(id: String)
    suspend fun createPost(content: String, imageIds: List<String>, parentPostId: String?): Post
    suspend fun deletePost(id: String)
}
