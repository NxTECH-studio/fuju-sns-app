package dev.fuju.core.domain

/**
 * UI → domain/data への入力型。`../frontend/src/types/vmInputs.ts` を移植。
 * camelCase のまま受け取り、network 層で swagger の snake_case に写像する。
 */
data class CreatePostInput(
    val content: String,
    val imageIds: List<String> = emptyList(),
    val parentPostId: String? = null,
)

data class UpdateProfileInput(
    val bio: String? = null,
    val bannerUrl: String? = null,
)

data class CreateBadgeInput(
    val key: String,
    val label: String,
    val description: String = "",
    val iconUrl: String = "",
    val color: String,
    val priority: Int,
)

data class UpdateBadgeInput(
    val label: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val color: String? = null,
    val priority: Int? = null,
)

data class GrantBadgeInput(
    val badgeKey: String,
    val expiresAt: String? = null,
    val reason: String? = null,
)

/**
 * Toast メッセージ種別。`../frontend/src/types/toast.ts` を移植。
 */
enum class ToastKind { INFO, SUCCESS, ERROR }

data class ToastItem(
    val id: String,
    val kind: ToastKind,
    val message: String,
)
