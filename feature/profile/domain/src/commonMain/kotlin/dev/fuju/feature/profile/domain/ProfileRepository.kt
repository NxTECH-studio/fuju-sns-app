package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput

/**
 * Backend `/users/*`, `/me` を叩く Repository の抽象。
 * `../frontend/src/api/endpoints/users.ts`, `me.ts`, `follows.ts` を移植。
 */
interface ProfileRepository {
    suspend fun getMe(): Me
    suspend fun listUsers(limit: Int = 20, offset: Int = 0): List<ProfileUser>
    suspend fun getUser(sub: String): ProfileUser
    suspend fun updateUser(sub: String, input: UpdateProfileInput): ProfileUser
    suspend fun follow(sub: String): FollowResult
    suspend fun unfollow(sub: String): FollowResult
    suspend fun followers(sub: String, limit: Int = 20, offset: Int = 0): List<ProfileUser>
    suspend fun following(sub: String, limit: Int = 20, offset: Int = 0): List<ProfileUser>
}
