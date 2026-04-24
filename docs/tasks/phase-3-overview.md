# Phase 3+ 実装計画 概要

`docs/tasks/migrate-fuju-sns-to-kmp.md` のフェーズ 3 以降を feature 単位で分解した index。`/start-with-plan <ファイル名>` で各タスクに入る。

## タスク一覧と推奨順序

| 順 | タスク | 元ステップ | 目安 | 依存 |
|---|---|---|---|---|
| 1 | [expand-navigation-and-shell.md](./expand-navigation-and-shell.md) | 17 拡張 | S | なし |
| 2 | [implement-timeline-feature.md](./implement-timeline-feature.md) | 14 | L | 1 |
| 3 | [implement-profile-feature.md](./implement-profile-feature.md) | 15 | M | 1, 2 |
| 4 | [implement-admin-feature.md](./implement-admin-feature.md) | 16 | S | 1 |
| 5 | [add-screenshot-tests.md](./add-screenshot-tests.md) | 22 | S | 2, 3 |

- **S = Small (1 PR, 〜1 日)**
- **M = Medium (1 PR, 2〜3 日)**
- **L = Large (1〜2 PR, 3〜5 日)**

## 共通の前提

- 参照: `../frontend/src/routes/`, `../frontend/src/hooks/`, `../auth-component/`
- Backend API: `../backend/docs/swagger.yaml` (Go `net/http`)
- AuthCore API: `../auth/docs/openapi.yaml`
- Repository の DTO 命名は snake_case → camelCase を `@SerialName` で吸収
- エラーハンドリングは `wrapAsAuthException` で統一
- UI は Compose Multiplatform + `core/ui` のデザイントークン

## 完了時のゴール状態

- Android / iOS で (1) ログイン (2) HomeTimeline 閲覧 (3) Post 投稿 (4) プロフィール表示 (5) フォロー/アンフォロー (6) ログアウト が通る
- Admin ユーザは Badge の CRUD と grant/revoke ができる
- 主要画面の screenshot test が baseline 付きで CI に乗る

## 現在の骨格状況（参考）

Phase 3 の `:feature:{timeline,profile,admin}/{data,domain,ui}` には Repository + 最小 UI が既に入っている。各タスクは **既存骨格を拡張する形** で進める（新規モジュール追加は不要）。

主な拡張ポイント:
- `feature/*/data/*RepositoryImpl.kt` — DTO と mapping を補完
- `feature/*/ui/*Screen.kt` — 実 UI を構築（現状はプレースホルダ）
- `composeApp/ComposeAppRoot.kt` — NavHost に route を追加、`PlaceholderScreen` を差し替え
