package dev.fuju.core.network.dto

import dev.fuju.core.domain.Badge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend `/v1/admin/badges` / `/users/{sub}` の `badges[]` に現れるバッジ DTO。
 * profile / admin feature で同一の JSON 形状のためここに寄せる。
 */
@Serializable
data class BadgeDto(
    val id: String,
    val key: String,
    val label: String,
    val description: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
    val color: String,
    val priority: Int,
) {
    fun toDomain(): Badge = Badge(id, key, label, description, iconUrl, color, priority)
}
