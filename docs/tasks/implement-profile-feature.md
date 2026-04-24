# Profile 機能の本実装

## 概要

`:feature:profile:{data,domain,ui}` の Repository + 最小 UI を本実装に差し替える。React 版 `UserProfileRoute.tsx` / `MyProfileEditRoute.tsx` / `FollowListRoute.tsx` / `FollowControl.tsx` と機能パリティを狙う。

## 背景・目的

- ユーザーページ（自分・他人）の表示（投稿一覧、Badge、フォロー数）
- フォロー / フォロワー一覧
- フォロートグル（Optimistic update）
- 自プロフィール編集（display name, bio, icon, banner）

## 影響範囲

- **変更**:
  - `feature/profile/data/.../ProfileRepositoryImpl.kt` — DTO / endpoint 補完
  - `feature/profile/ui/.../ProfileScreen.kt` — 実 UI 差し替え
- **新規**:
  - `feature/profile/ui/.../UserProfileScreen.kt`, `ProfileEditScreen.kt`, `FollowListScreen.kt`
  - `feature/profile/ui/.../FollowButton.kt` — Compose
  - `feature/profile/ui/.../ProfileViewModel.kt`
- **参照**:
  - `../frontend/src/routes/UserProfileRoute.tsx` / `MyProfileEditRoute.tsx` / `FollowListRoute.tsx` / `FollowControl.tsx`
  - `../frontend/src/hooks/useUserProfile.ts` / `useFollowToggle.ts` / `useFollowList.ts` / `useProfileEdit.ts`
- **依存**: `implement-timeline-feature.md` の `PostRow` を再利用（ユーザー投稿タイムライン表示のため）
- **破壊的変更**: なし

## 実装ステップ

1. **`01-port-user-dto`** — `/users/{sub}` レスポンスの完全な DTO を `ProfileRepositoryImpl.kt` に。Badge は既に `core/network/dto/BadgeDto.kt` にある共通型を流用。
2. **`02-port-follow-endpoints`** — `POST /users/{sub}/follow` / `DELETE /users/{sub}/follow` / `GET /users/{sub}/followers` / `GET /users/{sub}/following` の Repository メソッドを補完。
3. **`03-port-profile-edit-endpoints`** — `PUT /users/{sub}` で display name / bio 更新、`PUT /v1/user/icon` でアイコン更新（multipart、AuthRepository に実装済みの流用）。
4. **`04-build-profile-view-model`** — `ProfileViewModel(repository, sub: String?)`:
   - `sub = null` なら自分（`/me` + `/users/{sub}` 両方叩いて merge）
   - `StateFlow<ProfileState>` で `user`, `posts: PagedList<Post>`, `follow: FollowState` を持つ
5. **`05-build-user-profile-screen`** — Profile header（icon / banner / name / bio / badges）+ follow button + user timeline（`feature:timeline:ui` の `PostRow` を使う）。タブ切替で「投稿」「返信」「いいね」の 3 種を出すのは v2 送り。
6. **`06-build-follow-button`** — 相互フォロー、未フォロー、フォロー中の 3 状態を Optimistic update で toggle。
7. **`07-build-follow-list-screen`** — `UserListRow` + `LazyColumn` + pagination。follow button 付き。
8. **`08-build-profile-edit-screen`** — display name, bio, icon, banner の編集 form。`ProfileViewModel.update()` で反映。画像選択は timeline tag の image picker と同じ仕組み（expect/actual）。
9. **`09-wire-nav-host`** — `ComposeAppRoot` の NavHost に `Profile(publicId)` / `FollowList(sub, type)` / `ProfileEdit` を追加。User タップ → `Profile(publicId)` 遷移。
10. **`10-unit-tests`** — `ProfileViewModelTest` で follow toggle の Optimistic update を検証。
11. **`11-verify-and-commit`** — lint / compile green。エミュレータで自分 / 他人 / follow 操作を確認。

## テスト要件

- `ProfileViewModelTest`:
  - 自分と他人で読み込むエンドポイントが切り替わる
  - follow toggle で Optimistic update / rollback
  - profile edit で StateFlow が更新される
- 手動: 自分と他人のプロフィール、フォロー / アンフォロー、プロフィール編集

## 技術的な補足

- **follow state の 4 値**: not_following / following / followed_by / mutual。`core/domain/FollowResult.kt` に既存。
- **自分 vs 他人**: `/me` で得た `sub` と URL の `sub` を比較して判定。edit button 等の出し分けに使う。
- **アイコン / バナー upload**: `PUT /v1/user/icon` は multipart / form-data。Ktor の `submitFormWithBinaryData` を使う。既存 `AuthRepository.updateIcon` を参照。
- **画像キャッシュ**: Coil の default キャッシュに任せる。認証が必要な private 画像は後で対応。

## 参考

- `../frontend/src/routes/UserProfileRoute.tsx`
- `../frontend/src/routes/MyProfileEditRoute.tsx`
- `../frontend/src/routes/FollowListRoute.tsx`
- `../frontend/src/routes/FollowControl.tsx`
- `../frontend/src/hooks/useUserProfile.ts`
- `../frontend/src/hooks/useFollowToggle.ts`
- `../frontend/src/hooks/useFollowList.ts`
- `../frontend/src/hooks/useProfileEdit.ts`
