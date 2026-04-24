# Fuju SNS の Kotlin Multiplatform 化

## 概要

既存の React + TypeScript で書かれた `fuju-sns-frontend`（`../frontend/`）と `fuju-auth-react`（`../auth-component/`）を、Kotlin Multiplatform（KMP）プロジェクトとして `/home/sheep/dev/fuju/app/` に再構築する。UI は **Compose Multiplatform を共通レイヤとし、iOS 側は SwiftUI ラッパー**を被せる方針で確定済み。Backend は `../backend/`、認証基盤は `../auth/`（AuthCore）を参照する。

## 背景・目的

- 現状、SNS フロント（React SPA）と Auth 認証基盤コンポーネント（`fuju-auth-react`）は Web 向けのみ。iOS / Android でのネイティブ体験が必要。
- Web / iOS / Android でロジック（API クライアント、状態管理、認証フロー、バリデーション、エラーマッピング）を一元化して、仕様の三重管理を解消する。
- Android Studio Panda3 を主エディタとし、CI（GitHub Actions）で lint / build check を自動化できる状態をゴールとする。
- 既存 React 実装は成熟しており、移植のリファレンスとして十分に信頼できる（レイヤ境界、エラーハンドリング、Auth フローなどが整っている）。

## 確定した設計方針

- **UI 戦略**: Compose Multiplatform + iOS は SwiftUI ラッパー。iosApp の SwiftUI `App` から `ComposeUIViewController` をホストし、iOS 固有のネイティブ画面（カメラ、共有シートなど）のみ SwiftUI で個別実装する。
- **ターゲット**: Android / iOS のみ（Web / Desktop は対象外）。`androidTarget()`, `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`。
- **モジュール構成**: レイヤごとに細分化（`:core:*` + `:feature:*` + `:composeApp` + `:androidApp` + `:iosApp`）。
- **Bearer アクセストークン**: メモリ保持のみ。AuthCore の `refresh_token` は HttpOnly Cookie（`Path=/v1/auth`, `SameSite=Lax`, `Max-Age=2592000`）で、Ktor `HttpCookies(AcceptAllCookiesStorage)` が自動管理する。

## 影響範囲

- **新規作成**: `/home/sheep/dev/fuju/app/` 以下のすべて（KMP プロジェクト一式）。現状は `CLAUDE.md` と `.claude/` のみのグリーンフィールド。
- **参照のみ（変更しない）**:
  - `../frontend/`（`develop` ブランチ）: SNS 機能の仕様源。`src/api/`, `src/state/`, `src/hooks/`, `src/routes/`, `src/ui/`, `src/types/` のレイヤ構成を踏襲。
  - `../auth-component/`: AuthProvider / AuthGuard / useAuth / useAuthStatus / LoginForm / RegisterForm / MFAChallenge / MFASetupWizard / ProfileEditor / SocialSignupPublicIdForm / AuthErrorFallback の仕様源。`src/types.ts` と `src/ErrorCodes.ts`（34 種）は写経対象。
  - `../backend/`: REST API の契約（Go `net/http`, `http.ServeMux`, `docs/swagger.yaml` OpenAPI 3.0.3）。Bearer JWT 認証。
  - `../auth/`（AuthCore）: Cookie + Bearer 併用（Go `net/http`, `docs/openapi.yaml` OpenAPI 3.0.0）。
- **破壊的変更**: なし（新規リポジトリ）。移植作業中に API 契約の不整合を見つけたら backend / auth 側に Issue を立てて合わせる。

## モジュール構成（Gradle）

```
app/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── build.gradle.kts
├── composeApp/            # Compose Multiplatform UI エントリ + App Shell / Navigation
├── androidApp/            # MainActivity, AndroidManifest (Deep link)
├── iosApp/                # Xcode project. SwiftUI + ComposeUIViewController
├── core/
│   ├── domain/            # User, AuthStatus, Post, Timeline ... (commonMain only)
│   ├── error/             # ErrorCodes (34種), AuthException
│   ├── network/           # Ktor Client (engine: OkHttp / Darwin), Cookie, Serialization
│   ├── storage/           # expect/actual TokenStorage (in-memory + 永続ヒント interface)
│   └── ui/                # Theme, デザイントークン, 共通 Composable
└── feature/
    ├── auth/{data,domain,ui}
    ├── timeline/{data,domain,ui}
    ├── profile/{data,domain,ui}
    └── admin/{data,domain,ui}
```

レイヤ依存方向: `:feature:*:ui` → `:feature:*:domain` → `:feature:*:data` → `:core:*`。逆方向・`:feature:A` ↔ `:feature:B` の横断依存は Gradle モジュール宣言で物理的に禁止する。

## 実装ステップ

### フェーズ 0: セットアップ

1. **`01-setup-kmp-skeleton`** — `settings.gradle.kts`, `gradle/libs.versions.toml`, ルート `build.gradle.kts` を書き、上記モジュール構成のディレクトリ・空 `build.gradle.kts` を作成。Kotlin 2.x / AGP / Compose Multiplatform の versions を `libs.versions.toml` にロック。targets は `androidTarget()`, `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`。
2. **`02-configure-lint-and-format`** — `ktlint` + `detekt` + Compose Rules (`io.nlopez.compose.rules:ktlint`) + `spotless` + `.editorconfig` を導入。`./gradlew check` で全て回る状態にする。
3. **`03-wire-ios-xcode-project`** — `iosApp/` に SwiftUI App template を作成し、`composeApp` の XCFramework を import して `ComposeUIViewController` を表示できる最小構成まで組む（空画面）。

### フェーズ 1: 共通基盤（`:core:*`）

4. **`04-define-domain-types`** — `../auth-component/src/types.ts` と `../frontend/src/types/` を `:core:domain` に写経。`User`, `AuthStatus`(sealed), `SocialProvider`(enum: google / twitch / x), `LoginResult`(sealed), `MFASetupResult`, Post / Timeline / Badge の VM 型。`kotlinx.serialization` アノテーションを付与。
5. **`05-port-error-codes`** — `../auth-component/src/ErrorCodes.ts` の 34 種を `:core:error` に `enum class ErrorCode` として写経。`AuthException(code, status, retryAfterSec?)` を sealed で用意。
6. **`06-setup-ktor-client`** — `:core:network` に Ktor Client を構築。engine: OkHttp (Android) / Darwin (iOS)。`ContentNegotiation(Json)`, `HttpCookies(AcceptAllCookiesStorage)`, `Logging`, `DefaultRequest` で baseURL (`FUJU_API_BASE_URL`, `AUTH_CORE_BASE_URL`) を注入、`HttpTimeout`、Bearer を載せる `Auth` プラグイン。
7. **`07-implement-token-storage`** — `:core:storage` に `expect class TokenStorage` を定義し、actual は commonMain の in-memory 実装のみ。将来の KeyStore / Keychain 差し替えを想定して interface だけは切っておく。
8. **`08-implement-shared-ui-primitives`** — `:core:ui` に Theme / デザイントークン（`../frontend/` の CSS から抽出）、共通 Composable（Button, TextField, Toast, EmptyState, ErrorFallback）。

### フェーズ 2: 認証（`:feature:auth`）

9. **`09-implement-auth-data`** — `:feature:auth:data` に `AuthRepository` を実装。AuthCore の以下を呼ぶ: `POST /v1/auth/register`, `POST /v1/auth/login`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`, `POST /v1/auth/mfa/{register,enable,disable,verify}`, `GET /v1/auth/connect/{provider}`, `GET /v1/auth/callback/{provider}`, `DELETE /v1/auth/disconnect/{provider}/{providerUserId}`, `GET /v1/user/profile`, `PATCH /v1/user/public_id`, `PUT /v1/user/icon`。MFA verify は pre_token を Authorization header で送る。
10. **`10-implement-auth-store`** — `:feature:auth:domain` に `AuthStateMachine` を `StateFlow<AuthState>` + Use case 群で実装。`bootstrap → authenticating → (authenticated | mfa_required | unauthenticated | error)` の状態遷移と silent refresh タイマー（`kotlinx.coroutines` の `delay`）。`../auth-component/` の `AuthProvider` / `AuthStore` の責務を移植。
11. **`11-implement-auth-ui`** — `:feature:auth:ui` に Compose で `LoginForm`, `RegisterForm`, `MFAChallenge`, `MFASetupWizard`, `SocialSignupPublicIdForm`, `AuthErrorFallback`, `ProfileEditor` を実装。React 版の unstyled 方針は残さず、`:core:ui` のデザイントークンを当てる。
12. **`12-implement-auth-guard`** — `AuthGuard` Composable（`required`, `enforceMFA`, `fallback`）。未認証時は `onUnauthenticatedNavigate` 相当のラムダで呼び出し側のナビゲーションに委譲。
13. **`13-wire-oauth-callback`** — Social ログイン callback。Android は `AndroidManifest.xml` に intent-filter（Custom URL Scheme `fuju://auth/callback`）、iOS は `Info.plist` の `CFBundleURLTypes` + Universal Link。callback URL を AuthRepository に流し込むブリッジ関数を androidApp / iosApp に書く。

### フェーズ 3: SNS 機能

14. **`14-port-timeline`** — `:feature:timeline` 配下に Home / Global / User timeline, Post 詳細, 画像投稿, Like, Repost を実装。Backend: `GET /timeline/home` (Bearer), `GET /timeline/global`, `GET /timeline/user/{sub}`, `GET /posts/{id}`, `POST /posts`, `DELETE /posts/{id}`, `GET /posts/{id}/replies`, `POST/DELETE /posts/{id}/like`, `POST /v1/images`。ページングは React 版と同じ offset 方式。
15. **`15-port-profile`** — `:feature:profile` 配下にユーザプロフィール表示、フォロー / フォロワー一覧、プロフィール編集。Backend: `GET /users`, `GET/PUT /users/{sub}`, `POST/DELETE /users/{sub}/follow`, `GET /users/{sub}/{followers,following}`, `GET /me`。
16. **`16-port-admin`** — `:feature:admin` 配下に Admin Badges / Admin User Badges。Backend: `/v1/admin/badges/*` (Bearer + Admin)。ユーザ検索はクライアントサイドフィルタ（`../frontend/` README の暫定仕様を踏襲）。

### フェーズ 4: アプリ配線

17. **`17-wire-navigation`** — `:composeApp` に Navigation（Decompose / Voyager / Compose Navigation から選定）を組み込み、`/login` / `/timeline` / `/post/:id` / `/profile/:publicId` / `/admin/*` の各 route をマウント。
18. **`18-wire-android-app`** — `:androidApp` の `MainActivity` から `ComposeAppRoot()` を呼ぶ。Deep link (OAuth callback) を Manifest に登録。
19. **`19-wire-ios-app`** — `:iosApp` の SwiftUI `App` で `ComposeUIViewController` をホスト。iOS 固有画面（カメラ起動、共有シート等）があれば SwiftUI で個別実装し、Kotlin 側の state を `@Published` に橋渡しする adapter を用意。
20. **`20-configure-env-and-build-flavors`** — `FUJU_API_BASE_URL` / `AUTH_CORE_BASE_URL` / `SOCIAL_REDIRECT_URI` を BuildConfig（Android）と xcconfig（iOS）に流し込む。dev / staging / prod の 3 flavor を用意。

### フェーズ 5: 品質保証と CI

21. **`21-add-unit-tests`** — `commonTest` に Auth state machine、エラーマッピング、バリデータ（email / password / publicId）、Repository（Ktor `MockEngine`）のユニットテスト。
22. **`22-add-screenshot-tests`** — 主要画面（LoginForm, Timeline, Post detail, Profile）を `roborazzi` で screenshot test。
23. **`23-setup-github-actions`** — `.github/workflows/ci.yml` に lint (`./gradlew spotlessCheck ktlintCheck detekt`), Android build (`./gradlew :androidApp:assembleDebug`), iOS build (`xcodebuild build` on macOS runner), unit tests を Job で配置。`gradle/actions/setup-gradle` でキャッシュ。
24. **`24-android-studio-verification`** — Android Studio Panda3 で import し (a) Gradle sync 成功 (b) Android エミュレータでの起動 (c) Xcode 経由で iOS Simulator 起動 (d) Run/Debug 構成の自動生成 を確認。手順を `docs/dev-setup.md` にまとめる。

## テスト要件

**フェーズ 0 完了時点**:
- `./gradlew help` が通る
- Android Studio Panda3 で import して Gradle sync 成功
- `./gradlew check` で ktlint / detekt / spotless が green

**フェーズ 2 完了時点**:
- `commonTest` で AuthRepository のモック（Ktor `MockEngine`）経由のログイン → MFA verify → profile 取得がパス
- Android エミュレータで LoginForm → AuthCore 開発環境 (`http://localhost:8080`) に実ログインできる

**フェーズ 4 完了時点**:
- Android エミュレータ上で (a) アプリ起動 (b) AuthCore ログイン (c) Home Timeline 表示 (d) Post 詳細遷移 (e) ログアウト
- iOS Simulator で同じ 5 操作が可能
- Custom URL Scheme (`fuju://auth/callback`) 経由の Social ログイン callback が両 OS で動作

**フェーズ 5 完了時点**:
- GitHub Actions の PR チェックで lint / Android build / iOS build / unit tests が全て green
- `docs/dev-setup.md` の手順通りに別環境から import して動作

## 技術的な補足

- **フロントエンドは develop ブランチを参照**: 調査結果より `../frontend/` は既に develop チェックアウト済み。現状の仕様はすべて develop ベースで確認する。
- **Backend / AuthCore の共通スタック**: 両方 Go `net/http` + `http.ServeMux`。OpenAPI は Backend `docs/swagger.yaml`, AuthCore `docs/openapi.yaml`。開発環境はどちらも `http://localhost:8080`（ポート競合するので同時起動時は片方を別ポートに移す必要あり）。
- **AuthCore の Cookie 仕様**: `refresh_token` は HttpOnly `Secure` `SameSite=Lax` `Path=/v1/auth` `Max-Age=2592000`。Ktor `HttpCookies(AcceptAllCookiesStorage)` で自動管理する。Android は OkHttp engine 側の Cookie jar と競合しないよう、Ktor 側に一本化する。
- **OAuth（Social ログイン）の callback**: Web では `location.assign` だが、モバイルは Custom URL Scheme `fuju://auth/callback`（Android intent-filter / iOS `CFBundleURLTypes`）+ 本番は Universal Link / App Link。provider（Google / Twitch / X）の OAuth アプリ側 Redirect URI 設定変更も必要。
- **React 版の制約継承**: Admin ユーザ検索のクライアントサイドフィルタ暫定は KMP 版でも踏襲する（backend 側に search API が入るまで）。

## 関連ドキュメント

- ヒアリング付きの設計プラン: `/home/sheep/.claude/plans/partitioned-orbiting-llama.md`
- UI 戦略の決定記録: `docs/adr/0001-ui-strategy.md`（フェーズ 0 以降で作成）
