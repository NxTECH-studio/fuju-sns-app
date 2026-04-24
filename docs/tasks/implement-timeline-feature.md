# Timeline 機能の本実装

## 概要

`:feature:timeline:{data,domain,ui}` に入っている Repository + 最小 UI を本実装に差し替える。React 版 `../frontend/src/routes/HomeTimelineRoute.tsx` / `GlobalTimelineRoute.tsx` / `PostDetailRoute.tsx` / `ComposerBox.tsx` / `PostRow.tsx` と機能パリティを狙う。

## 背景・目的

- Home / Global timeline を offset ページングで表示
- Post 投稿（テキスト + 画像添付 + 返信 / repost）
- Post 詳細画面（親 post + 返信一覧）
- Like / Repost トグル
- React 版の `useTimeline` / `usePagedList` / `usePostActions` の状態管理パターンを踏襲

## 影響範囲

- **変更**:
  - `feature/timeline/data/src/commonMain/kotlin/.../TimelineRepositoryImpl.kt` — DTO 補完、image multipart upload 実装
  - `feature/timeline/domain/src/commonMain/kotlin/.../TimelineQueries.kt` — ページング state の定義を追加（hasMore, loading, error）
  - `feature/timeline/ui/src/commonMain/kotlin/.../TimelineList.kt` — 実 UI に差し替え
- **新規**:
  - `feature/timeline/ui/.../HomeTimelineScreen.kt`, `GlobalTimelineScreen.kt`, `PostDetailScreen.kt`, `ComposerScreen.kt`
  - `feature/timeline/ui/.../PostRow.kt` — 共通行レイアウト
  - `feature/timeline/ui/.../TimelineViewModel.kt` — StateFlow ベースの ViewModel 相当（KMP 共通）
- **参照**: `../frontend/src/routes/HomeTimelineRoute.tsx`, `../frontend/src/hooks/useTimeline.ts`, `usePagedList.ts`, `usePostActions.ts`
- **破壊的変更**: なし

## 実装ステップ

1. **`01-port-timeline-dtos`** — Backend の `/timeline/*` レスポンスに合わせて `PostDto`, `AuthorDto`, `OGPDto`, `PostImageDto`, `PostTagDto` を補完。`TimelineRepositoryImpl.kt` の既存 stub を埋める。
2. **`02-port-paging-model`** — `core/domain` または `feature/timeline/domain` に `PagedList<T>(items, offset, hasMore, loading, error)` を定義。`usePagedList` のロジックを移す。
3. **`03-port-like-repost-endpoints`** — `POST /posts/{id}/like` / `DELETE /posts/{id}/like` の既存実装に Optimistic Update を追加（`usePostActions.ts` と同じ挙動）。失敗時は rollback。
4. **`04-port-image-upload`** — `POST /v1/images` multipart upload を `AuthRepository.updateIcon` と同じ要領で実装。返ってきた `image_id` を `POST /posts` の body に含める。
5. **`05-build-timeline-view-model`** — `TimelineViewModel(repository, type: Home|Global|User)` を KMP 共通で実装。`StateFlow<PagedList<Post>>` を expose、`loadMore()` / `refresh()` / `likePost(id)` / `unlikePost(id)` メソッドを持つ。CoroutineScope は呼び出し側（Compose の `rememberCoroutineScope`）から注入、または `viewModelScope` 相当を自前で管理。
6. **`06-build-post-row`** — `PostRow` Composable。ユーザーアイコン（Coil）、名前、時刻、本文、画像グリッド、Like / Reply / Repost ボタン、OGP カードを含める。
7. **`07-build-home-timeline-screen`** — `HomeTimelineScreen(vm: TimelineViewModel, onPostClick: (id) -> Unit, onUserClick: (publicId) -> Unit)` を実装。`LazyColumn` + `items(list.items, key = { it.id })` + bottom reached で `loadMore`。Pull-to-refresh は Compose 標準 `PullToRefreshBox`。
8. **`08-build-global-timeline-screen`** — 同上。異なる ViewModel type で Home と違うだけ。共通化できる部分は抽出。
9. **`09-build-post-detail-screen`** — 親 post + 返信一覧 + 返信 composer。Backend: `GET /posts/{id}` + `GET /posts/{id}/replies`。
10. **`10-build-composer-screen`** — 新規 post / 返信兼用。Modal bottom sheet または full screen. Image picker は Android `ActivityResultContracts.GetContent`、iOS は Swift 側 adapter（ブリッジは shared の `expect fun pickImage(): ByteArray?` など）
11. **`11-wire-nav-host`** — `ComposeAppRoot` の NavHost に `HomeTimelineScreen` / `GlobalTimelineScreen` / `PostDetailScreen` / `ComposerScreen` を配線。`onPostClick` から `navController.navigate(FujuDestination.PostDetail(postId))`。
12. **`12-unit-tests`** — `TimelineViewModelTest` を `commonTest` に追加。`MockEngine` で Repository を駆動、`Turbine` で StateFlow 検証。loadMore / refresh / like optimistic update をテスト。
13. **`13-verify-and-commit`** — lint / compile green。手動: エミュレータで Home にスクロール / タップ / Like 反映 / 投稿が通るか確認。

## テスト要件

- `TimelineViewModelTest`（common, `MockEngine` + `Turbine`）:
  - 初期ロード: items が API 結果と一致
  - loadMore: offset が加算され append される
  - like optimistic update: UI は即座に反映、失敗時 rollback
  - refresh: offset リセット + 再取得
- 手動: Home / Global 切替、画像付き post 作成、Like、返信
- CI: `./gradlew :feature:timeline:data:allTests :feature:timeline:domain:allTests`（Android SDK 環境）

## 技術的な補足

- **ページング方式**: React 版と同じ offset 方式。Backend が `has_more` を返すならそれを信じる。返さないなら `items.size < limit` で判定。**Backend 確認必要**（`../backend/docs/swagger.yaml`）。
- **画像アップロード**: Compose Multiplatform に共通の image picker がないため、Android / iOS で expect/actual を切る。最初は Android 側だけ実装し、iOS は next task に回すのが現実的。
- **Coil の設定**: `core/ui` に既に `coil-compose` / `coil-network-ktor` 依存。`AsyncImage(model = url, ...)` で使える。Bearer 必要な画像は Coil 用の専用 HttpClient を `AppContainer` から expose する必要あり。
- **Optimistic update**: `TimelineViewModel` の StateFlow を直接いじる形で OK。失敗時は before 値を保持して rollback。
- **OGP card**: React 版には `OGPPreview` 型あり。`core/domain/Post.kt` にも `OGPPreview` がある想定（要確認）。

## 参考: React 版の相当箇所

- `../frontend/src/routes/HomeTimelineRoute.tsx` — 画面構造
- `../frontend/src/routes/GlobalTimelineRoute.tsx`
- `../frontend/src/routes/PostDetailRoute.tsx`
- `../frontend/src/routes/ComposerBox.tsx` — Composer
- `../frontend/src/routes/PostRow.tsx` — Row layout
- `../frontend/src/hooks/useTimeline.ts` — state flow
- `../frontend/src/hooks/usePagedList.ts` — paging
- `../frontend/src/hooks/usePostActions.ts` — like/repost
- `../frontend/src/hooks/useImages.ts` — upload
