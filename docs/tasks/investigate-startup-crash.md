# Android 起動直後クラッシュの原因調査・修正

## 概要

Android Studio から Run した直後（エミュレータ上で最初の画面が出る前）にアプリがクラッシュする。スタックトレース未入手のため、コード inspection で最も可能性の高い候補を順に検証して修正する。ベースブランチは `feature/kmp-skeleton`、作業は `fix/startup-crash` で行う。

あわせて、実運用で使用する backend / auth エンドポイントが `https://auth.fujupay.app` / `https://snsapi.fujupay.app` であることが確定したため、flavor のデフォルト URL も localhost から実 URL に差し替える（cleartext HTTP の懸念は消える）。

## 背景・目的

- PR #1 (`feature/kmp-skeleton`) を Windows 環境の Android Studio で Run したところ、起動直後にクラッシュ。
- Logcat / ADB ログは未取得のため、**仮説駆動のデバッグ**で対処する。
- 最低限「空画面が出る」まで持っていくのがこのタスクのゴール。本格的な UI 実装は別 PR。
- Backend / AuthCore は `auth.fujupay.app` / `snsapi.fujupay.app` (HTTPS) で運用するため、localhost 向けのデフォルトや cleartext 許可は不要。

## 主な仮説（優先度順）

### 仮説 A: `:composeApp` に `kotlin.serialization` plugin が未適用（最有力）

- `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/nav/FujuDestination.kt` は `@Serializable sealed interface` を使用。
- ところが `composeApp/build.gradle.kts` の `plugins { ... }` ブロックには `alias(libs.plugins.kotlin.multiplatform)` / `compose.multiplatform` 等しかなく、**`alias(libs.plugins.kotlin.serialization)` が欠落**。
- `@Serializable` アノテーションはメタデータだけのため、プラグインがないとシリアライザが生成されず、NavHost の `composable<T> { ... }` が route 型のシリアライザを探しに行く段階で `SerializationException: Serializer for class 'Login' is not found` 等を throw。
- Compose の最初の composition 時点、つまり `setContent { ... }` の直下で発火するので「起動直後にクラッシュ」と整合。

### 仮説 B: JetBrains navigation-compose `2.9.0-alpha16` と Compose Multiplatform 1.8.2 / Kotlin 2.2.20 のバージョン組合せ不整合

- `libs.versions.toml`: `kotlin = "2.2.20"`, `compose-multiplatform = "1.8.2"`, `jetbrains-navigation = "2.9.0-alpha16"`。
- ローカル compile (`compileKotlinIosX64`, `compileCommonMainKotlinMetadata`) は通っているが、Android 側で runtime に dependency resolution が壊れる可能性がある。
- 対策: 仮説 A を直しても落ちる場合、`jetbrains-navigation` を別バージョン（`2.9.0-alpha17` / `2.8.0`）に調整するか、一時的に NavHost を外して素の Compose だけで描画させて切り分け。

### 仮説 C: その他

- `android:Theme.Material.Light.NoActionBar` は minSdk 26 で存在するので問題にならないはず。
- `ComposeAppRoot` の `LaunchedEffect(Unit) { deps.authStateMachine.bootstrap() }` は suspend function なので main thread ブロックではない。
- `AppContainer` 初期化は synchronous だが lazy（Ktor HttpClient は最初のリクエストで engine 初期化）。
- **Cleartext HTTP は懸念外**: `auth.fujupay.app` / `snsapi.fujupay.app` は HTTPS のため `usesCleartextTraffic` も `networkSecurityConfig` も不要。localhost 向け例外も追加しない。

## 影響範囲

- **修正対象**:
  - `composeApp/build.gradle.kts` — プラグイン追加
  - `androidApp/build.gradle.kts` — dev/staging/prod flavor のデフォルト URL を `auth.fujupay.app` / `snsapi.fujupay.app` に変更
  - `iosApp/Configuration/Config.xcconfig.example` — デフォルト URL を同様に変更
  - `iosApp/iosApp/iOSApp.swift` — ハードコードされた `http://localhost:8081` を `https://auth.fujupay.app` 等に差し替え
  - `.env.example` — コメント例示を実 URL に更新
  - `docs/dev-setup.md` — flavor 表 / セットアップ手順の URL 更新
- **参照**:
  - `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/nav/FujuDestination.kt`
  - `composeApp/src/commonMain/kotlin/dev/fuju/composeApp/ComposeAppRoot.kt`
  - `gradle/libs.versions.toml`
- **破壊的変更**: dev flavor の挙動が HTTPS 本番エンドポイントを指すようになるため、本当にローカル backend を叩きたい開発者は `local.properties` で `fuju.apiBaseUrl=http://10.0.2.2:8080` 等を override する必要がある（README / dev-setup に明記）。

## 実装ステップ

1. **`01-fix-serialization-plugin`** — `composeApp/build.gradle.kts` の `plugins { ... }` に `alias(libs.plugins.kotlin.serialization)` を追加（本命の fix）。
2. **`02-update-flavor-defaults`** — `androidApp/build.gradle.kts` の 3 flavor 全部を以下に変更:
   - `apiBaseUrl` → `https://snsapi.fujupay.app`
   - `authCoreBaseUrl` → `https://auth.fujupay.app`
   - `socialRedirectUri` は `fuju://auth/callback` のまま
   - 開発者がローカル backend を叩きたい場合は `local.properties` で override（現状の仕組みで対応可能）。
3. **`03-update-ios-defaults`** — `iosApp/iosApp/iOSApp.swift` の `IOSAppSession.init` をハードコード HTTP から HTTPS に差し替え。`iosApp/Configuration/Config.xcconfig.example` も同様に更新。
4. **`04-update-env-example-and-docs`** — `.env.example` の例示値と `docs/dev-setup.md` の flavor 表 / セットアップ手順の URL を `auth.fujupay.app` / `snsapi.fujupay.app` に更新。ローカル開発したい場合の override 手順を追記。
5. **`05-verify-compile-lint`** — `./gradlew spotlessCheck ktlintCheck detekt :composeApp:compileCommonMainKotlinMetadata :composeApp:compileKotlinIosX64` が引き続き通ることを確認。
6. **`06-commit-push`** — fix コミット + `fix/startup-crash` ブランチに push（PR 作成は `/pr-create` で別途）。
7. **`07-user-verify`** — ユーザー側 Android Studio で Run し、起動クラッシュが解消されたか確認。再発時は logcat の FATAL EXCEPTION 行を共有してもらう。

## テスト要件

- `./gradlew :composeApp:compileCommonMainKotlinMetadata` と `:composeApp:compileDebugKotlinAndroid`（SDK 必要なのでユーザー環境）が green。
- Android エミュレータで起動し、最低限 Login 画面が表示される（認証は失敗しても OK、落ちないこと）。
- Logcat に `SerializationException` / `kotlinx.serialization.SerializationException` が出ていない。
- `BuildConfig.FUJU_API_BASE_URL` が `https://snsapi.fujupay.app`、`AUTH_CORE_BASE_URL` が `https://auth.fujupay.app` を返す（flavor 既定時）。

## 技術的な補足

- **`kotlin-serialization` plugin を他モジュールではどう扱っているか**: `core/domain` / `core/network` / `feature/auth/data` などは既にプラグインを適用済み（`libs.versions.toml` に `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }` あり）。composeApp だけ漏れていた形。
- **環境ごとの URL 分離**: 本タスクでは dev/staging/prod を同じ `auth.fujupay.app` / `snsapi.fujupay.app` に寄せる。将来 `auth-staging.fujupay.app` / `snsapi-dev.fujupay.app` のようなサブドメインが必要になったら、`androidApp/build.gradle.kts` の `envFlavors` Map を書き換えるだけで対応できる設計。
- **ローカル backend を叩く運用**: 開発者が手元で backend / auth-core を起動している場合は、`local.properties` に以下を書けば override される（既存の仕組み）:
  ```properties
  fuju.apiBaseUrl=http://10.0.2.2:8080
  fuju.authCoreBaseUrl=http://10.0.2.2:8081
  ```
  この場合は別途 `network_security_config` または `usesCleartextTraffic=true` が必要。ただし本タスクでは **デフォルト HTTPS を前提**とするため、その設定は加えない。必要になったら別タスクで flavor 別 Manifest を整備する。
- **仮説 A / flavor URL 更新を直しても落ちる場合の次の一手**:
  - Android Studio の Logcat を開いたまま Run → 赤字 (ERROR) / FATAL EXCEPTION 行を貼ってもらう。
  - `adb logcat -b crash` で crash buffer だけ取得。
  - 仮説 B のバージョン組合せを疑う（navigation-compose を一時的に外して `ComposeAppRoot` を裸で描画）。

## 参考コミット

このブランチの直前コミット: `f93eba3` (fix: address OAuth / Gradle code review findings)
