# テスト戦略

## Unit tests (`commonTest`)

KMP の `commonTest` に、ロジック層（`:core:*`, `:feature:*:domain`, `:feature:*:data`）
のテストを書く。UI 層（`:feature:*:ui`）は次節の screenshot test に寄せる。

| モジュール | テスト内容 |
|-----------|-----------|
| `:core:domain` | `Validators` (email / password / publicId) |
| `:core:error` | `ErrorCode.fromWireOrUnknown` 網羅 |
| `:core:storage` | `InMemoryTokenStorage` の setter / clear |
| `:feature:auth:domain` | `AuthStateMachine` の状態遷移 (Turbine) |
| `:feature:auth:data` | `AuthRepository` (Ktor `MockEngine`) |

### 実行

```bash
# Android SDK が必要
./gradlew allTests
```

- 全ターゲット (`androidUnitTest`, `iosX64Test`, `iosSimulatorArm64Test`) を同時に走らせる
- CI では Android + JVM 分だけ。iOS native test は macOS runner に任せる

## Screenshot tests (roborazzi)

`:feature:*:ui` の Compose 画面を `roborazzi` で PNG として保存し、`git diff` で
視覚回帰を検出する。2026-04 時点の公式手順:

```kotlin
// 例: LoginFormScreenshotTest.kt
@get:Rule val composeTestRule = createComposeRule()
@get:Rule val roborazziRule = RoborazziRule()

@Test
fun loginForm_initial() {
    composeTestRule.setContent {
        FujuTheme { LoginForm(...) }
    }
    composeTestRule.onRoot().captureRoboImage()
}
```

- 実行環境: Robolectric + JVM
- ベースライン更新: `./gradlew recordRoborazziDebug`
- 検証: `./gradlew verifyRoborazziDebug`

本プロジェクトでは次のフェーズでベースライン画像を格納するため、現時点では
テストコードのみ用意し CI では無効化している。

## 参考

- [Roborazzi](https://github.com/takahirom/roborazzi)
- [Turbine](https://github.com/cashapp/turbine)
- [Ktor MockEngine](https://ktor.io/docs/client-testing.html)
