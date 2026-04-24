# Screenshot test のベースライン整備 (roborazzi)

## 概要

`docs/testing.md` で roborazzi の設定方針は書いたが、実テストコード + baseline PNG は未生成。timeline / profile / auth の主要画面に screenshot test を追加し、CI で回帰検知する。

## 背景・目的

- UI 変更時に見た目の退行を自動検知したい
- Compose プレビューを手動確認に頼らない
- 最低限 LoginForm / HomeTimelineScreen / PostDetailScreen / UserProfileScreen の 4 画面をカバー

## 影響範囲

- **新規**:
  - `feature/auth/ui/src/androidUnitTest/kotlin/.../LoginFormScreenshotTest.kt`
  - `feature/timeline/ui/src/androidUnitTest/kotlin/.../HomeTimelineScreenshotTest.kt` ほか
  - `feature/*/ui/src/androidUnitTest/roborazzi/*.png`（baseline）
- **変更**:
  - `feature/*/ui/build.gradle.kts` — `alias(libs.plugins.roborazzi)` 適用、`screenshot` source set、`robolectric` 依存
  - `.github/workflows/ci.yml` — `./gradlew verifyRoborazzi` を Android job に追加
- **依存**: `implement-timeline-feature.md` + `implement-profile-feature.md` 完了後が望ましい
- **破壊的変更**: なし

## 実装ステップ

1. **`01-wire-roborazzi-per-module`** — 対象モジュール（feature/auth/ui, feature/timeline/ui, feature/profile/ui）に `alias(libs.plugins.roborazzi)` と `testImplementation(libs.roborazzi)` / `libs.roborazzi.compose` / `libs.roborazzi.rule` / `libs.robolectric` を追加。
2. **`02-add-test-infrastructure`** — 共通の `ComposeTestRule + RoborazziRule` をセットアップ。fake `AuthSnapshot` / `User` / `Post` を供給する test builders を作る。
3. **`03-write-login-screenshot`** — `LoginFormScreenshotTest` で `FujuTheme { LoginForm(...) }` を描画、`captureRoboImage()` で baseline PNG 生成。初回は `./gradlew recordRoborazziDebug` でベースラインを commit。
4. **`04-write-timeline-screenshots`** — Home timeline / Post row / empty state / error state の 4 パターン。
5. **`05-write-profile-screenshots`** — User profile header / follow button 3 状態 / profile edit form。
6. **`06-verify-in-ci`** — `.github/workflows/ci.yml` の Android lint/build job 後に `./gradlew :feature:auth:ui:verifyRoborazziDebug :feature:timeline:ui:verifyRoborazziDebug :feature:profile:ui:verifyRoborazziDebug` を追加。diff があれば PR をブロック。
7. **`07-document`** — `docs/testing.md` を更新して baseline 再生成コマンド（`recordRoborazziDebug`）と diff 確認コマンドを書く。

## テスト要件

- CI で `verifyRoborazzi` が通る（baseline と 0px 差）
- 意図的に UI を変更した PR では `recordRoborazziDebug` で新規 baseline を commit する運用

## 技術的な補足

- **KMP モジュールでの roborazzi**: `androidUnitTest` source set で使う。`commonTest` には置けない（Android SDK 必須）。
- **フォント差分**: Roboto などシステムフォントに依存すると OS / emulator で差が出る。Roborazzi の `pixelBitConfig` で対応するか、fixed font を bundle する。最初は default で、差分が出たら対応。
- **画像 (Coil)**: network 経由の画像は test で fetch しない。`AsyncImage` の placeholder をモックするか、test で固定色 bitmap を差し込む。
- **PR で baseline が diff**: `./gradlew recordRoborazziDebug` を実行した .png をそのまま commit する運用。

## 参考

- https://github.com/takahirom/roborazzi
- `docs/testing.md` — roborazzi セットアップ方針
