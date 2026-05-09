package dev.fuju.composeApp.nav

import kotlinx.serialization.Serializable

/**
 * NavHost の型安全 route 定義。`androidx.navigation:navigation-compose` の
 * `composable<T>(...)` + `kotlinx.serialization` と組み合わせて使う。
 *
 * Destination のインスタンスをそのまま `NavController.navigate(dest)` に渡せる。
 *
 * frontend `../frontend/src/routes/router.tsx` の URL 設計に揃える:
 * - `/` (start) → [GlobalTimeline]
 * - `/posts/:id` → [PostDetail]
 * - `/users/:sub` → [Profile] / [MyProfile]
 * - `/users/:sub/{followers,following}` → [FollowList]
 * - `/settings` / `/settings/profile` → [SettingsRoot] / [SettingsProfile]
 * - `/admin/badges` / `/admin/users` → [AdminBadges] / [AdminUserBadges]
 *
 * 旧 `HomeTimeline` / `ProfileEdit` は削除（Home は frontend で撤去済み、
 * ProfileEdit は SettingsProfile に統合）。
 */
sealed interface FujuDestination {
    @Serializable data object Login : FujuDestination

    // --- Top-level tabs (shell の NavigationBar に表示) ---

    /** Global timeline タブ。公開投稿の全体一覧。start destination。 */
    @Serializable data object GlobalTimeline : FujuDestination

    /** 自分のプロフィールタブ。Profile(publicId=me) と意味的に同じだが route 型として分離する。 */
    @Serializable data object MyProfile : FujuDestination

    /** 設定ハブ。`SettingsRoot` 配下に各セクション (`SettingsProfile` 等) を配置する。 */
    @Serializable data object SettingsRoot : FujuDestination

    /** 管理者バッジマスタータブ（`/admin/badges`）。isAdmin のみ表示。 */
    @Serializable data object AdminBadges : FujuDestination

    // --- 子画面 (shell 内のスタック遷移) ---

    @Serializable data class PostDetail(
        val postId: String,
    ) : FujuDestination

    /**
     * 他人のプロフィール画面。`publicId` は backend 上の `sub` (ULID)。
     * 自プロフィールは [MyProfile] を使うこと。
     */
    @Serializable data class Profile(
        val publicId: String,
    ) : FujuDestination

    /**
     * フォロワー / フォロー中の一覧画面。
     * @param sub 対象ユーザーの sub（ULID）
     * @param followers true: フォロワー一覧、false: フォロー中一覧。enum を直接載せると
     *   serialization まわりが増えるので bool に寄せている
     */
    @Serializable data class FollowList(
        val sub: String,
        val followers: Boolean,
    ) : FujuDestination

    /**
     * 設定 > プロフィール編集（`/settings/profile`）。
     * 旧 `ProfileEdit` を統合した先。`SettingsRoot` 経由で表示するため shell では
     * 単独の destination としても受け、deeplink / navigate を維持する。
     */
    @Serializable data object SettingsProfile : FujuDestination

    /** 管理者: ユーザーへのバッジ付与 / 剥奪（`/admin/users`）。isAdmin のみ表示。 */
    @Serializable data object AdminUserBadges : FujuDestination
}
