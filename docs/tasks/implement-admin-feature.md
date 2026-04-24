# Admin 機能の本実装

## 概要

`:feature:admin:{data,domain,ui}` に入っている Repository + 最小 UI を、React 版 `routes/admin/` と機能パリティになるように本実装する。Badge の CRUD / grant / revoke と、クライアントサイドユーザー検索が主。

## 背景・目的

- Admin は Badge マスタの管理（作成 / 更新 / 削除）
- Badge をユーザーに grant / revoke
- ユーザー検索はクライアントサイドフィルタで暫定運用（`../frontend/` README の backlog P2-13 に合わせる）
- 運用者のみが触る画面なので、UI は凝らず実用優先

## 影響範囲

- **変更**:
  - `feature/admin/data/.../AdminRepositoryImpl.kt` — `/v1/admin/badges/*`, `/v1/admin/users/{sub}/badges/*` の実装は既にあるので、DTO 補完 + mapping
  - `feature/admin/ui/.../AdminBadgesScreen.kt` — 実 UI 差し替え
- **新規**:
  - `feature/admin/ui/.../AdminBadgeEditScreen.kt` — Badge 1 件の CRUD 画面
  - `feature/admin/ui/.../AdminUserBadgesScreen.kt` — ユーザー別 Badge 付与画面
  - `feature/admin/ui/.../AdminUserSearchBox.kt` — client-side filter
  - `feature/admin/ui/.../AdminViewModel.kt`
- **参照**:
  - `../frontend/src/routes/admin/BadgesRoute.tsx` 等（存在する前提、要確認）
  - `../frontend/src/hooks/useAdminBadges.ts`, `useUsers.ts`
- **依存**: `expand-navigation-and-shell.md` の Admin タブ追加
- **権限チェック**: 現状は UI ガードなし（backend 側の 403 頼み）。UI で `user.isAdmin` を判定する仕組みは別タスク（要 `/me` レスポンスに `is_admin` が入っているか確認）

## 実装ステップ

1. **`01-confirm-admin-endpoints`** — `../backend/docs/swagger.yaml` で `/v1/admin/*` が全て必要分揃っているか確認。欠けていたら backend チームに issue。
2. **`02-build-admin-view-model`** — `AdminViewModel(repository)`:
   - `badges: StateFlow<List<Badge>>`
   - `users: StateFlow<List<ProfileUser>>`（全件 load して client-side filter）
   - `createBadge()`, `updateBadge(id, input)`, `deleteBadge(id)`, `grantBadge(sub, input)`, `revokeBadge(sub, key)`
3. **`03-build-admin-badges-list`** — `AdminBadgesScreen`: Badge 一覧 + "新規" FAB。タップで `AdminBadgeEditScreen` に遷移。
4. **`04-build-admin-badge-edit`** — `AdminBadgeEditScreen(id: String?)`: create / update 共用。label, description, icon_url, color, priority のフォーム。
5. **`05-build-admin-user-list-with-search`** — `AdminUserBadgesScreen`: `TextField` に入力すると `users.filter { it.displayName.contains(query) || it.publicId.contains(query) }` で client filter。選択で grant 画面に遷移。
6. **`06-build-admin-user-badges-detail`** — 選択ユーザーに付いている Badge 一覧 + grant / revoke ボタン。
7. **`07-wire-nav-host`** — `ComposeAppRoot` の Admin タブ配下に `AdminBadges` / `AdminBadgeEdit(id)` / `AdminUsers` / `AdminUserBadges(sub)` を配線。
8. **`08-unit-tests`** — `AdminViewModelTest` で client-side filter / CRUD を検証。
9. **`09-verify-and-commit`** — lint / compile green。エミュレータで手動確認（Admin 権限のテストアカウントが必要、backend staging 環境で）。

## テスト要件

- `AdminViewModelTest`:
  - client-side filter が期待通り絞る（大文字小文字 / 部分一致）
  - Badge CRUD で StateFlow が更新される
  - grant / revoke の Optimistic update
- 手動: Badge 作成 → User に grant → Revoke の一連の flow。Admin 権限がないアカウントでは 403 を受けて error 表示。

## 技術的な補足

- **クライアント側の Admin gate**: `/me` の `is_admin` フラグで Admin タブを hide する。フラグが backend にまだ無いなら UI では全員に表示し、API で 403 を受けて error フォールバック表示（暫定）。backend にフラグ追加 issue を立てる。
- **全ユーザー load**: `../frontend/` 版と同じく全ユーザーを一度取って filter。Staging では数百人程度の想定。production で増えたら search API を backend で実装する。
- **Badge の priority**: Int、降順で sort。Badge 一覧は priority 順にソートして表示。

## 参考

- `../frontend/src/routes/admin/` 配下
- `../frontend/src/hooks/useAdminBadges.ts`
- `../frontend/src/hooks/useUsers.ts`
- `../backend/docs/swagger.yaml` の `/v1/admin/*` セクション
