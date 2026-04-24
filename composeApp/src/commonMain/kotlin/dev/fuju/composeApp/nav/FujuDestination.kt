package dev.fuju.composeApp.nav

import kotlinx.serialization.Serializable

/**
 * NavHost の型安全 route 定義。`androidx.navigation:navigation-compose` の
 * `composable<T>(...)` + `kotlinx.serialization` と組み合わせて使う。
 *
 * Destination のインスタンスをそのまま `NavController.navigate(dest)` に渡せる。
 */
sealed interface FujuDestination {
    @Serializable data object Login : FujuDestination

    @Serializable data object HomeTimeline : FujuDestination

    @Serializable data object GlobalTimeline : FujuDestination

    @Serializable data class PostDetail(
        val postId: String,
    ) : FujuDestination

    @Serializable data class Profile(
        val publicId: String,
    ) : FujuDestination

    @Serializable data object AdminBadges : FujuDestination
}
