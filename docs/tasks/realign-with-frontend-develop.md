# フロントエンド develop に合わせて全面再構築

## 概要

KMP/Compose Multiplatform 側の SNS 機能を、`../frontend/`（`origin/develop`）の最新仕様に揃えて全面的に書き直す。frontend は直近で **Home timeline 廃止 / 画像投稿廃止 / `/settings` ハブ追加 / Admin 完成** といった大きな構造変更を入れているが、KMP 側は旧仕様（Home + Global タブ、画像投稿あり、`/me/edit` 単独、Admin プレースホルダ）のままなので、ナビゲーション・API・UI を一気に再構築する。

## 背景・目的

- 現状の KMP 側実装は frontend の旧仕様に基づく **仮実装** として組まれており、frontend の最新 UI/振る舞いと乖離している。
- frontend `origin/develop` の最近のコミット（KMP 側に未反映）:
  - `feat(timeline): drop home timeline, unify on global at /` — Home タイムライン廃止、ルートは Global のみ
  - `feat(images): remove image upload feature from frontend` — 画像投稿 UI を撤去
  - `feat(settings): add /settings hub and migrate profile editor` — `/settings/profile` を新設、`/me/edit` はリダイレクトのみ
  - Admin Badges / Admin User Badges を完全実装
- 個別の差分を順次追従すると一貫性が崩れるので、**今回はまとめて一気に書き直す**。frontend の React 実装を仕様源とし、`api/types.ts` → `services/mappers.ts` → `types/vm.ts` → `routes/`/`ui/` のレイヤを KMP の `:core` / `:feature:*:data,domain,ui` / `:composeApp` に写経し直す。

## 影響範囲

### 変更対象モジュール

- **`:composeApp`** — `nav/FujuDestination.kt`、`shell/FujuShell.kt`、`ComposeAppRoot.kt`。NavigationBar の構成、start destination、Composer FAB 表示条件を変える。
- **`:feature:timeline:*`** — `TimelineKind.Home` を撤去し `Global` 一本に統合。`TimelineRepository.createPost` から `imageIds` を削除（あるいは常に空）。`PostRow.kt` / `PostDetailScreen.kt` の画像表示は **読み取り専用** で残す（既存投稿の画像は表示する）。`ComposerDialog.kt` から画像ピッカー UI を撤去。
- **`:feature:profile:*`** — `ProfileEditScreen.kt` を `/settings/profile` 相当の構造に再配置（後述の Settings shell から呼ぶ）。フォーム文言を frontend に合わせる（「display_name / display_id / アイコンは AuthCore 側で編集」の注記など）。
- **`:feature:admin:*`** — 現状の `AdminBadgesScreen.kt` プレースホルダを破棄し、`AdminBadgesRoute.tsx` + `AdminUserBadgesRoute.tsx` 相当を実装。`AdminRepository` に Badge の list / create / update / delete / grant / revoke を実装。
- **`:core:domain` / `:core:network`** — VM 型と DTO を frontend の `api/types.ts` / `types/vm.ts` の最新スキーマに揃える（Badge / PublicUser / SelfUser / Post / OGPPreview ほか、name 規約が `*VM` ベースになっている部分を再点検）。
- **テスト**: `feature/*/ui/src/androidUnitTest/` の Roborazzi スクリーンショットテストは画面構成変更で baseline が ずれる → 撮り直し。`feature/timeline/domain/.../TimelineViewModelTest.kt` などの fake/test も TimelineKind の変更に追従。

### 破壊的変更

- **あり**（KMP 側のみ）。frontend / backend / AuthCore には触らない。
- アプリ内で「Home タブ」「画像投稿」を使えなくなる。tab order と Composer の挙動が変わる。

### 参照のみ（変更しない）

- `../frontend/`（`origin/develop`）— 全レイヤの仕様源。
- `../auth-component/` — 認証 UI / Hook の仕様源（今回の書き直し対象には含めない、認証フローはそのまま）。
- `../backend/` `../auth/` — REST API 契約。エンドポイント変更は今回しない。

## 実装ステップ

### フェーズ A: ドメイン/データ層の再アライン

1. **`01-sync-domain-types`** — `:core:domain` の Post/User/Badge/OGP/FollowResult を frontend `src/types/vm.ts` の最新フィールドに揃える。命名は KMP 慣習に合わせて camelCase のまま、`*VM` サフィックスは付けない（KMP では `domain` レイヤがそのまま VM）。`PostImage` 型は **読み取り専用 VM** として残す（画像表示は維持）。
2. **`02-sync-network-dtos`** — `:core:network` / `feature:*:data` の `*Dtos.kt` と mapper を `../frontend/src/api/types.ts` + `src/services/mappers.ts` に揃える。`UpdateUserProfileRequest` が `bio` + `banner_url` のみであることを確認、不要フィールドを削除。
3. **`03-drop-image-upload-from-data`** — `TimelineRepository.createPost` の `imageIds: List<ULID>` パラメータを削除し、リクエスト DTO `CreatePostRequest` から `image_ids` を取り除く（送らないなら省略でも可、frontend の挙動を確認）。`feature:timeline:data` の Mock/Test/Fake を更新。`/v1/images` を呼ぶコードがあれば物理削除。
4. **`04-merge-timeline-kinds`** — `:feature:timeline:domain` の `TimelineKind` から `Home` を削除し `Global` / `User(sub)` の 2 つに集約。`TimelineQueries` の `/timeline/home` 呼び出しを削除。`TimelineViewModelTest` と `FakeTimelineRepository` を追従。

### フェーズ B: 認証ガード / Shell / Navigation の再構築

5. **`05-redefine-destinations`** — `composeApp/nav/FujuDestination.kt` を以下に再定義（frontend `routes/router.tsx` 準拠）:
   - `GlobalTimeline` (start, route `/`)
   - `PostDetail(postId)`
   - `Profile(sub)` / `MyProfile`
   - `FollowList(sub, followers: Boolean)`
   - `SettingsRoot` / `SettingsProfile`（nested）
   - `AdminBadges` / `AdminUserBadges`
   - `Login`
   - 旧 `HomeTimeline` / `ProfileEdit` は削除（後者は `SettingsProfile` に統合）。
6. **`06-rewrite-shell-tabs`** — `FujuShell.kt` の `NavigationBar` を frontend の左ナビ（`RootLayout` の `navLinks`）に揃える。タブは **Global / Profile / Settings / Admin（管理者のみ表示）** の最大 4 件。`canLike` 判定は据え置き。`isAdmin` を `authStateMachine` 経由で取得し、Admin タブ表示を条件分岐する。
7. **`07-update-fab-visibility`** — Composer FAB は `GlobalTimeline` / `PostDetail`（返信用）でのみ表示。`HomeTimeline` 関連の分岐は削除。
8. **`08-migrate-me-edit-redirect`** — 既に `ProfileEdit` route から push される箇所（プロフィール画面の「編集」ボタンなど）は `SettingsProfile` を navigate するように差し替える。`ProfileEdit` route 自体は削除。

### フェーズ C: Settings ハブの追加

9. **`09-add-settings-shell`** — `composeApp/shell/SettingsShell.kt`（あるいは `:feature:profile:ui` に `SettingsScreen.kt`）を新設。frontend `routes/SettingsRoute.tsx` + `ui/components/SettingsNav.tsx` を踏襲し、左カラムに項目リスト、右カラムに `Outlet` 相当の子 Composable を表示する 2 ペイン構造。タブ単一（プロフィールのみ）でも将来追加用に拡張可能な形で組む。
10. **`10-port-settings-profile-section`** — `feature:profile:ui` に `SettingsProfileSection.kt` を作成し、frontend `routes/settings/SettingsProfileSection.tsx` を写経。bio (max 500) と banner_url (max 1024) の 2 フィールド + 注意書き（display_name 等は AuthCore 側）+ 保存後 `/users/{sub}` へ navigate。`useProfileEdit` 相当のロジックは既存 `ProfileViewModel.update*` を再利用。Toast は `:core:ui` の Toast コンポーネントで表示。

### フェーズ D: Admin 機能の実装

11. **`11-implement-admin-repository`** — `:feature:admin:data` の `AdminRepositoryImpl` で以下を実装（`../backend/docs/swagger.yaml` 準拠）:
    - `GET /v1/admin/badges`
    - `POST /v1/admin/badges`
    - `PUT /v1/admin/badges/{id}`
    - `DELETE /v1/admin/badges/{id}`
    - `POST /v1/admin/users/{sub}/badges` (grant)
    - `DELETE /v1/admin/users/{sub}/badges/{badgeId}` (revoke)
12. **`12-port-admin-badges-screen`** — `:feature:admin:ui` の `AdminBadgesScreen.kt` を frontend `AdminBadgesRoute.tsx` + `BadgeForm.tsx` に揃えて再実装。一覧 + 「新規作成」ボタン + 編集モード + 削除確認ダイアログ。`isAdmin` 以外がアクセスしたら `GlobalTimeline` へ replace navigate。
13. **`13-port-admin-user-badges-screen`** — `AdminUserBadgesScreen.kt` を新規作成し `AdminUserBadgesRoute.tsx` を写経。ユーザ一覧（クライアントサイドフィルタで検索）+ ユーザ選択 → そのユーザに付与済みの badge リスト + 付与/剥奪 UI。
14. **`14-add-admin-viewmodels`** — `feature:admin:domain` に `AdminBadgesViewModel` / `AdminUserBadgesViewModel` を追加（既存の `ProfileViewModel` 相当の StateFlow パターン）。

### フェーズ E: テスト/品質保証

15. **`15-update-screenshot-baselines`** — `feature/*/ui/src/androidUnitTest/` の既存 Roborazzi テストを画面構成変更に合わせて修正し、`./gradlew recordRoborazziDebug` で baseline PNG を撮り直してコミット。撤去する `HomeTimelineScreenshotTest` は削除、追加する `SettingsProfileScreenshotTest` / `AdminBadgesScreenshotTest` は新規作成。
16. **`16-update-domain-tests`** — `TimelineViewModelTest` / `ProfileViewModelTest` 等の fake / 期待値を新スキーマに追従。`AdminViewModelTest` を新規追加。
17. **`17-manual-verification`** — Android エミュレータでビルド → 起動 → ログイン → Global timeline 表示 → 投稿（テキストのみ）→ Post 詳細 → Profile → Settings/Profile 編集 → Admin（管理者ユーザで）まで通しで動くことを目視確認。

## テスト要件

- `./gradlew check` が green（ktlint / detekt / spotless / unit test）。
- `./gradlew :androidApp:assembleDebug` が成功。
- `./gradlew verifyRoborazziDebug` が新 baseline で green。
- 主要フロー（手動）:
  - 起動 → Global タイムライン表示
  - 投稿（テキストのみ、画像 UI なし）
  - Post 詳細 → 既存画像があれば表示される（読み取り）
  - プロフィール → 「編集」 → `/settings/profile` 相当画面で bio / banner を保存 → ユーザページに戻る
  - 管理者ユーザでログイン時のみ Admin タブが見える、Admin Badges の作成/編集/削除と User Badges の付与/剥奪が動く

## 技術的な補足

- **画像投稿は撤去するが表示は残す**: frontend は `images` 配列の表示自体は維持しているので、`Post.images` モデルと `PostRow` の画像レンダリングはそのまま。投稿フォームから画像追加 UI のみ消す。
- **`isAdmin` の出所**: AuthCore の self user (`/me` envelope) に `is_admin` が乗ってくる。`feature:auth:data` の `SelfUser` DTO と `AuthSnapshot` に `isAdmin: Boolean` がすでにある想定（なければ `02-sync-network-dtos` で追加）。
- **Settings の nested 構造**: KMP Navigation Compose の `navigation { ... }` で nested graph を組んでもよいが、現状は flat NavHost で運用している。今回は SettingsShell を Composable レベルで 2 ペイン化し、内部で sub-Composable を切り替える形が単純（route は `SettingsProfile` 1 個のみで済む）。将来項目が増えたら nested graph に再編する。
- **タブ表示の `isAdmin` 連動**: NavigationBar の items を `remember(authSnapshot.isAdmin) { ... }` で組み立てる。Admin に居る最中に admin 権限を失うケースは想定外（即時ログアウト扱い）。
- **frontend 側の `ToastProvider` / `MeProvider` 相当**: KMP 側はそれぞれ `:core:ui` の Toast Composable と `AuthStateMachine.state` で代替済み。新規追加は不要。
- **画像投稿撤去のスコープ**: 画像 **upload** API (`POST /v1/images`) は backend にまだ存在するが、KMP からの呼び出しは全て削除する。撤去後の関連テストは消すか `@Ignore`。
- **`/me/edit` deeplink**: 旧 deeplink を維持する場合は、Android Manifest / iOS の URL handler 側で `/settings/profile` へ rewrite する処理を追加。今回は KMP アプリは内部 navigation のみで `/me/edit` を持たないので不要。

## 参考

- frontend (`origin/develop`):
  - `src/routes/router.tsx`（ルート定義）
  - `src/routes/RootLayoutRoute.tsx` / `src/ui/layouts/RootLayout.tsx`（shell 構造）
  - `src/routes/SettingsRoute.tsx` / `src/routes/settings/SettingsProfileSection.tsx`
  - `src/routes/admin/AdminBadgesRoute.tsx` / `AdminUserBadgesRoute.tsx`
  - `src/services/mappers.ts` / `src/types/vm.ts` / `src/api/types.ts`
- backend: `../backend/docs/swagger.yaml`
- 既存 KMP 移行計画: `docs/tasks/migrate-fuju-sns-to-kmp.md`（フェーズ完了後の補正版が本タスク）
