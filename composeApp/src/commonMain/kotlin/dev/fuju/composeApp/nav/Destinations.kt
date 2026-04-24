package dev.fuju.composeApp.nav

/**
 * 画面の遷移先。`androidx.navigation` を導入するフェーズ 4 以降で route path と
 * 対応づけるが、現状は sealed な tag として使う。
 */
sealed class FujuDestination(val label: String) {
    data object Login : FujuDestination("Login")
    data object HomeTimeline : FujuDestination("Home")
    data object GlobalTimeline : FujuDestination("Global")
    data class PostDetail(val postId: String) : FujuDestination("Post")
    data class Profile(val publicId: String) : FujuDestination("Profile")
    data object AdminBadges : FujuDestination("Admin Badges")
}
