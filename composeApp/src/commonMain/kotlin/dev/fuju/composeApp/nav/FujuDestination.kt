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

    // --- Top-level tabs (shell の NavigationBar に表示) ---

    /** Home timeline タブ。自分のフォロー中ユーザーの投稿一覧。 */
    @Serializable data object HomeTimeline : FujuDestination

    /** Global timeline タブ。公開投稿の全体一覧。 */
    @Serializable data object GlobalTimeline : FujuDestination

    /** 自分のプロフィールタブ。Profile(publicId=me) と意味的に同じだが route 型として分離する。 */
    @Serializable data object MyProfile : FujuDestination

    /** 管理者バッジ一覧タブ。 */
    @Serializable data object AdminBadges : FujuDestination

    // --- 子画面 (shell 内のスタック遷移) ---

    @Serializable data class PostDetail(
        val postId: String,
    ) : FujuDestination

    @Serializable data class Profile(
        val publicId: String,
    ) : FujuDestination
}
