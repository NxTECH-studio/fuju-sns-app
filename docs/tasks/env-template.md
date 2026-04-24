# 環境変数テンプレートの整備

## 概要

AuthCore / SNS Backend の URL と OAuth redirect URI を、リポジトリに commit された 1 箇所のテンプレート (`.env.example`) で宣言し、各開発者の `local.properties` / `iosApp/Configuration/Config.xcconfig` にコピーして上書きできるようにする。現状は flavor ごとに `androidApp/build.gradle.kts` と `iosApp/Configuration/Config.xcconfig` にハードコードされているため、ローカル開発用に URL を差し替えるたびにビルドスクリプトを触る必要がある。

## 背景・目的

- ローカル AuthCore / Backend は環境によってポートやホストが違う（Windows の WSL / macOS / Linux、ngrok 転送、リモート開発サーバなど）ため、ビルドスクリプトに書かれた `http://10.0.2.2:8080` だと毎回書き換えが必要。
- 新メンバーが「どの URL を設定すれば良いか」を掴みづらい。まとめて記載したテンプレートがほしい。
- secret ではない設定（URL / scheme 等）なので `.env.example` はリポジトリに含めるが、実体 (`.env` / `local.properties` / `Config.xcconfig`) は `.gitignore` で除外する。

## 影響範囲

- **新規**:
  - `.env.example` — 全環境変数の宣言 + 各 flavor のデフォルト値と上書き手順
  - `iosApp/Configuration/Config.xcconfig.example` — iOS 開発者向けテンプレート
- **変更**:
  - `androidApp/build.gradle.kts` — `local.properties` を読み込み、欠けているキーは flavor 既定値を使う
  - `iosApp/Configuration/Config.xcconfig` — 実体を `.gitignore` に入れて、開発者は `.example` からコピーする運用に変更（または別名の `Config.dev.xcconfig` / `Config.staging.xcconfig` / `Config.prod.xcconfig` を commit して例示に使う）
  - `.gitignore` — `local.properties` はすでに ignore 済み。`iosApp/Configuration/Config.xcconfig` も ignore 対象に追加（`*.example` は除外）
  - `docs/dev-setup.md` — セットアップ手順に「`.env.example` → `local.properties` / `Config.xcconfig` にコピーして値を埋める」を明記
- **破壊的変更**: `iosApp/Configuration/Config.xcconfig` を gitignore にするので、既存開発者は手元の値を `.example` に合わせてコピーし直す必要がある

## 実装ステップ

1. **`01-create-env-example`** — リポジトリルートに `.env.example` を作成。全変数をコメント付きで列挙:
   ```
   # AuthCore のベース URL。Android エミュレータからは http://10.0.2.2:8081、
   # iOS Simulator からは http://localhost:8081。実機は LAN IP か staging URL。
   AUTH_CORE_BASE_URL=http://10.0.2.2:8081
   FUJU_API_BASE_URL=http://10.0.2.2:8080
   SOCIAL_REDIRECT_URI=fuju://auth/callback
   # dev / staging / prod のうちどれを使うか (Android: build variant, iOS: Xcode scheme)
   # FUJU_ENV=dev
   ```
2. **`02-wire-gradle-local-properties`** — `androidApp/build.gradle.kts` で `rootProject.file("local.properties")` を読み、`fuju.apiBaseUrl` / `fuju.authCoreBaseUrl` / `fuju.socialRedirectUri` キーがあれば `buildConfigField` を上書きする。欠けていれば現在の flavor 既定値を使うフォールバックを残す。
3. **`03-add-config-xcconfig-example`** — `iosApp/Configuration/Config.xcconfig.example` を作成し、現行の `Config.xcconfig` を `.gitignore` へ移動。新規開発者は `cp Config.xcconfig.example Config.xcconfig` で開始する。既存のコミットされた値は `.example` にコピーして残す。
4. **`04-update-gitignore`** — `.gitignore` に `iosApp/Configuration/Config.xcconfig` を追加し、`iosApp/Configuration/Config.xcconfig.example` は除外（先頭 `!`）。`local.properties` は既に ignore 済み。
5. **`05-update-dev-setup`** — `docs/dev-setup.md` の「初回セットアップ」に `.env.example` → `local.properties` / `Config.xcconfig` のコピー手順を追記。Android エミュレータと iOS Simulator でホスト名が異なる (`10.0.2.2` vs `localhost`) 注意点も追加。
6. **`06-verify`** — `./gradlew :androidApp:assembleDevDebug` 相当の sanity check。`local.properties` なしでも fallback 値でビルドできることを確認する。

## テスト要件

- `local.properties` なしで `./gradlew :androidApp:assembleDevDebug` が成功し、`BuildConfig.AUTH_CORE_BASE_URL` が現在の flavor 既定値になる
- `local.properties` に `fuju.authCoreBaseUrl=https://my.ngrok.example.com` を書いて同じコマンドを実行すると、生成された `BuildConfig.AUTH_CORE_BASE_URL` がその値になる（`./gradlew :androidApp:processDevDebugResources` の出力か、生成 APK を `aapt dump` で確認）
- iOS: `Config.xcconfig.example` をコピーして `Config.xcconfig` に書き換え、Xcode で dev スキーマがビルドできる

## 技術的な補足

- `local.properties` の読み込みは Android Gradle Plugin の標準パターンを使う:
  ```kotlin
  val localProps = java.util.Properties().apply {
      val f = rootProject.file("local.properties")
      if (f.exists()) load(f.inputStream())
  }
  val authCoreBaseUrl = localProps.getProperty("fuju.authCoreBaseUrl")
      ?: "http://10.0.2.2:8081"  // flavor 既定値
  buildConfigField("String", "AUTH_CORE_BASE_URL", "\"$authCoreBaseUrl\"")
  ```
- `Config.xcconfig` を ignore しない代替案: `Config.dev.xcconfig` / `Config.staging.xcconfig` / `Config.prod.xcconfig` の 3 ファイルをリポジトリに commit し、Xcode スキーマで切替える。今回はシンプルさを優先し、1 ファイルを開発者ごとに書き換える運用にする。
- `.env` 形式を直接読む Gradle プラグイン（`io.github.cdsap.dotenv` 等）は導入しない。Android の `local.properties` は既に KeyStore パス等で使われている標準的な場所であり、追加ツールは不要。
- iOS 側で xcconfig を Swift に流し込むには、Build Settings の User-Defined 変数として参照し、`Info.plist` に `$(VAR_NAME)` で展開する。既存の `Info.plist` に該当キーがないので、必要に応じて `FUJUAPIBaseURL` などの Info.plist エントリを追加して `Bundle.main.object(forInfoDictionaryKey:)` で読む。今回スコープは「テンプレート整備」に限り、Swift 側の参照変更は別タスクに分離する。
