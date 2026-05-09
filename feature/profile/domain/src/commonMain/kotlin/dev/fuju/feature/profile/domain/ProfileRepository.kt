package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput

/**
 * Backend `/users/{sub}` 系と `/me` を叩く Repository の抽象。
 * `../frontend/src/api/endpoints/users.ts`, `me.ts`, `follows.ts` を移植。
 *
 * ページング方式:
 * - `listUsers` は offset paging
 * - `followers` / `following` は cursor paging（`FollowListQuery.cursor` / `FollowListPage.nextCursor`）
 */
interface ProfileRepository {
    suspend fun getMe(): Me

    suspend fun listUsers(
        limit: Int = 20,
        offset: Int = 0,
    ): UserListPage

    suspend fun getUser(sub: String): ProfileUser

    /**
     * 自分のプロフィールを更新する。backend 側で `is_admin` が返るため domain 側は
     * [Me] として返す（`/me` と同じ形）。
     */
    suspend fun updateUser(
        sub: String,
        input: UpdateProfileInput,
    ): Me

    suspend fun follow(sub: String): FollowResult

    suspend fun unfollow(sub: String): FollowResult

    suspend fun followers(
        sub: String,
        query: FollowListQuery = FollowListQuery(),
    ): FollowListPage

    suspend fun following(
        sub: String,
        query: FollowListQuery = FollowListQuery(),
    ): FollowListPage
}

/**
 * follower / following 一覧取得のページングパラメータ。`FollowListResponse` に合わせて
 * cursor ベース。swagger の `OpaqueCursorParam` を踏襲し、`cursor` は opaque な文字列のまま
 * 次ページ取得に載せて返す。
 */
data class FollowListQuery(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT: Int = 30
    }
}

/** follower / following 一覧の 1 ページ分の応答。 */
data class FollowListPage(
    val items: List<ProfileUser>,
    val nextCursor: String?,
)

/**
 * `GET /v1/users` の offset paging 応答。`UserListResponse` の domain 表現。
 */
data class UserListPage(
    val items: List<ProfileUser>,
    val limit: Int,
    val offset: Int,
    val total: Int,
)
