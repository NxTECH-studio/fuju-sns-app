# Navigation とアプリシェルの拡張

## 概要

現状 `ComposeAppRoot` は認証状態に応じて `FujuNavHost` に単純な NavHost を持つだけで、タブ / ドロワー / ボトム共通 UI がない。React 版の `RootLayoutRoute.tsx` に相当する shell を Compose で実装し、Home / Global / Profile / Admin への遷移導線を整える。

Phase 3 の timeline / profile / admin 実装で画面が増えた時に困らないよう、このタスクを先に通す。

## 背景・目的

- 現状は Login → HomeTimeline の 2 画面しか実質配線されておらず、他の destination は `PlaceholderScreen` のまま
- bottom navigation / top app bar / logout ボタン等の shell UI が無いと timeline 実装後もユーザーは別画面に移れない
- 将来 Home / Global / Profile / (Admin) の 4 タブ構成にする前提で、導線を用意する

## 影響範囲

- 変更: `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/ComposeAppRoot.kt` — NavHost を拡張、shell を追加
- 変更: `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/nav/FujuDestination.kt` — 必要なら tab ルート追加
- 新規: `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/shell/FujuShell.kt` — Scaffold + BottomBar
- 参照: `../frontend/src/routes/RootLayoutRoute.tsx`, `../frontend/src/routes/router.tsx`
- 破壊的変更: なし（新規 destination を追加するだけ。既存の Login は残る）

## 実装ステップ

1. **`01-add-tab-destinations`** — `FujuDestination` に `Home`, `Global`, `Profile`, `Admin` の top-level tab を追加（`@Serializable data object`）。既存の `HomeTimeline` / `GlobalTimeline` / `AdminBadges` を参考に命名統一。
2. **`02-create-fuju-shell`** — `FujuShell` Composable を新設。Material3 の `Scaffold` + `NavigationBar`（ボトムタブ）で Home / Global / Profile / Admin を切替え。現 tab は `currentBackStackEntryAsState` で追跡。top bar はタイトル + logout アクション。
3. **`03-nest-nav-graphs`** — 各 tab に子 NavHost を持たせて、PostDetail / Profile(publicId) / AdminBadges へのスタック遷移を許可。実装が重いなら **最初はフラット NavHost でもよい**（nested は後続で可）。
4. **`04-wire-logout`** — top bar のメニューから `authStateMachine.logout()` を呼べるようにする。`LaunchedEffect(snapshot.status)` で Unauthenticated に遷移したら Login に飛ばす既存ロジックをそのまま活用。
5. **`05-replace-placeholder-in-authenticated-branch`** — `ComposeAppRoot` の `when (Authenticated) { FujuNavHost(...) }` を `FujuShell(...)` に差し替える。Login 画面は shell の外のまま（認証前なので bottom bar は不要）。
6. **`06-verify`** — ktlint / spotless / detekt / compileCommonMainKotlinMetadata / compileKotlinIosX64 が green。Android エミュレータで shell が表示され、タブ切替が動くことをユーザーが確認。

## テスト要件

- 自動テスト: tab 切替の手動 state 管理ではなく `NavController` に委ねているので、`rememberNavController()` を Compose test で包んだ smoke test を追加（optional、次タスク 05 に任せても良い）
- 手動: Android で Run → 認証後に 4 タブ表示 / 切替 / top bar の logout で Login に戻る

## 技術的な補足

- `NavigationBar` + `NavigationBarItem` で選択状態は `currentDestination?.hierarchy?.any { it.hasRoute(tab::class) }` で判定（`androidx.navigation:navigation-compose` 2.8+ 相当の API、JetBrains fork も同一）。
- **タブ永続化**: `navigate(tab) { popUpTo(graph.findStartDestination()) { saveState = true }; restoreState = true; launchSingleTop = true }` で状態を保存。
- iOS で SwiftUI 的な tab bar と整合性を取りたい場合は次タスクで Swift 側ラッパーを検討。今回は Compose 側で完結させる。
- React 版 `RootLayoutRoute.tsx` の構造（top bar + main + bottom? を確認）。もし drawer 方式なら方針を変える（AskUser）。
