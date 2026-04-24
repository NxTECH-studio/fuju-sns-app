# 開発環境セットアップ

## 前提

- **Android Studio Panda3** (Koala 以降でも動くはずだが、Panda3 で検証済み)
- **JDK 21** (Temurin / OpenJDK)
- **Xcode 16+** (iOS ビルド時。macOS のみ)
- **Kotlin 2.2**, **Compose Multiplatform 1.8**, **Gradle 9.3** は
  `gradle/libs.versions.toml` と `gradle/wrapper/gradle-wrapper.properties` に固定。

## リポジトリ構成

```
app/
├── composeApp/          # Compose Multiplatform UI エントリ + App Shell / Navigation
├── androidApp/          # MainActivity, AndroidManifest (Deep link)
├── iosApp/              # SwiftUI ラッパー（Xcode project は別途生成）
├── core/
│   ├── domain/          # User, AuthStatus, Post, Timeline ...
│   ├── error/           # ErrorCode 列挙 (34種 + UNKNOWN), AuthException
│   ├── network/         # Ktor Client (OkHttp / Darwin), Cookie, Serialization
│   ├── storage/         # TokenStorage (in-memory), SessionHintStore
│   └── ui/              # Theme, デザイントークン, 共通 Composable
└── feature/
    ├── auth/{data,domain,ui}
    ├── timeline/{data,domain,ui}
    ├── profile/{data,domain,ui}
    └── admin/{data,domain,ui}
```

## 初回セットアップ

1. JDK 21 をインストールし、`JAVA_HOME` を設定する。
2. Android Studio Panda3 を起動し、`Open...` で `/.../fuju/app/` を選ぶ。
3. Gradle sync を待つ（プラグイン / Gradle 9.3 の自動ダウンロード）。
4. `local.properties` に Android SDK パスが自動記入されていることを確認。
5. **環境変数テンプレートをローカルにコピーする** (`.env.example` 参照):
   - Android: デフォルトで HTTPS 本番エンドポイント
     (`https://auth.fujupay.app` / `https://snsapi.fujupay.app`) を指すため、通常は
     `local.properties` に URL を書く必要はない（Android SDK パスのみで動く）。
     ローカル backend を叩きたい場合だけ以下を追記する:
     ```properties
     fuju.authCoreBaseUrl=http://10.0.2.2:8081
     fuju.apiBaseUrl=http://10.0.2.2:8080
     fuju.socialRedirectUri=fuju://auth/callback
     ```
     Android エミュレータからホスト PC を指すには `10.0.2.2` を使う。
     cleartext HTTP を使う場合は別途 AndroidManifest の `usesCleartextTraffic` /
     `networkSecurityConfig` を整備する必要がある（本リポジトリのデフォルトでは未設定）。
   - iOS: `cp iosApp/Configuration/Config.xcconfig.example iosApp/Configuration/Config.xcconfig`
     してから値を書き換える（デフォルトは本番 HTTPS）。`Config.xcconfig` 本体は
     `.gitignore` に入っているため commit されない。
     ローカル backend を叩く場合は `http://localhost:8080` 等に書き換える。
6. Run/Debug 構成プルダウンに **`androidApp [dev]`** が表示される
   （`.idea/runConfigurations/` に commit 済み）。
7. 左側の **Build Variants** パネルで `androidApp` の Active Build Variant を
   `devDebug` に設定する（初期値は `stagingDebug` や `prodDebug` になる場合がある）。

### Run ボタンがグレーアウトする場合

- Gradle sync が完了していない → `Sync Project with Gradle Files` を再実行
- `androidApp [dev]` の `module name="fuju-app.androidApp.main"` が合わない場合は
  一度削除して Android Studio が自動生成する run config を利用する
- Android SDK が `local.properties` に未設定 → `File → Project Structure → SDK Location` で設定

## よく使うコマンド

```bash
# lint / format / static analysis 一括
./gradlew spotlessCheck ktlintCheck detekt

# Kotlin 全ターゲットのユニットテスト
./gradlew allTests

# Android APK (dev flavor / debug)
./gradlew :androidApp:assembleDevDebug

# iOS Simulator framework (macOS + Xcode 必須)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## iOS ビルド

`iosApp/iosApp.xcodeproj` はリポジトリに含めない（pbxproj の手書き管理は事故を
生みやすいため）。以下のいずれかで生成する:

- Android Studio: `Tools → Kotlin → KMP → Generate iOS Xcode Project`
- あるいは https://kmp.jetbrains.com/ のテンプレートから生成し、`iosApp/iOSApp.swift`
  と `Info.plist` を差し替える。

生成後は Xcode で Open し、Framework Search Paths に
`$(SRCROOT)/../composeApp/build/bin/iosSimulatorArm64/...` を追加、`Run` すれば
iOS Simulator で起動する。

## flavor 設定

`:androidApp` に `dev` / `staging` / `prod` の 3 flavor を用意している。

| flavor | applicationId | API | Auth |
|--------|---------------|-----|------|
| dev | dev.fuju.app.dev | `https://snsapi.fujupay.app` | `https://auth.fujupay.app` |
| staging | dev.fuju.app.staging | `https://snsapi.fujupay.app` | `https://auth.fujupay.app` |
| prod | dev.fuju.app | `https://snsapi.fujupay.app` | `https://auth.fujupay.app` |

現状は 3 flavor 全てが同じ HTTPS 本番エンドポイントを指している。将来
`auth-staging.fujupay.app` / `snsapi-dev.fujupay.app` のようなサブドメインが
必要になったら、`androidApp/build.gradle.kts` の `envFlavors` Map を書き換える。

iOS は `iosApp/Configuration/Config.xcconfig` に同じキーを持たせ、Xcode スキーマ
側で切替える（フェーズ 4 で 3 スキーマに分岐）。

上表は flavor の既定値。`local.properties` に `fuju.apiBaseUrl` /
`fuju.authCoreBaseUrl` / `fuju.socialRedirectUri` を書くと、すべての flavor で
その値が優先される（`androidApp/build.gradle.kts` の `localOrDefault(...)` 参照）。
ローカル backend を叩くために cleartext HTTP URL を override する場合は、別途
AndroidManifest の `usesCleartextTraffic` / `networkSecurityConfig` 整備が必要。
詳細は `.env.example` を参照。

## 参照リポジトリ

- Fuju フロントエンド (React): `../frontend/` (develop branch)
- Auth Component (React): `../auth-component/`
- Fuju Backend (Go `net/http`): `../backend/`
- AuthCore (Go `net/http`): `../auth/`

## トラブルシュート

- Gradle sync が失敗する場合、`./gradlew --stop` で daemon を止めて再試行。
- `kotlin-ios-simulator-arm64` target のリンクで失敗する場合、Xcode Command Line
  Tools が入っているか確認: `xcode-select --install`。
- Compose Compiler のバージョン不整合が出たら `gradle/libs.versions.toml` の
  `compose-multiplatform` と `kotlin` のマトリクスを確認する。
