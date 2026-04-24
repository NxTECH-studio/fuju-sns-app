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

`:feature:*:ui` の Compose 画面を `roborazzi` で PNG として保存し、pixel diff で
視覚回帰を検出する。2026-04 時点の公式手順:

```kotlin
// 例: LoginFormScreenshotTest.kt
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginFormScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()
    @get:Rule val roborazziRule =
        RoborazziRule(
            composeRule = composeTestRule,
            captureRoot = composeTestRule.onRoot(),
            options = RoborazziRule.Options(
                outputDirectoryPath = "src/androidUnitTest/roborazzi",
            ),
        )

    @Test
    fun loginForm_initial() {
        composeTestRule.setContent { FujuTheme { LoginForm(...) } }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/LoginForm_initial.png")
    }
}
```

- 実行環境: Robolectric + JVM（Android SDK が必須）
- baseline PNG の保存先: `feature/*/ui/src/androidUnitTest/roborazzi/*.png`
- プロジェクト設定: 各 `feature/*/ui/build.gradle.kts` で `alias(libs.plugins.roborazzi)` を適用し、`androidUnitTest` に `libs.roborazzi` / `libs.roborazzi.compose` / `libs.roborazzi.rule` / `libs.robolectric` を入れている

### 初回 baseline 生成

`feature:{auth,timeline,profile}:ui` で baseline PNG を生成する。

```bash
# 全モジュール一括
./gradlew :feature:auth:ui:recordRoborazziDebug \
          :feature:timeline:ui:recordRoborazziDebug \
          :feature:profile:ui:recordRoborazziDebug

# 生成された PNG を commit する
git add feature/*/ui/src/androidUnitTest/roborazzi/
git commit -m "test: add roborazzi baseline screenshots"
```

生成される baseline:

| モジュール | 画像 |
|-----------|------|
| `:feature:auth:ui` | `LoginForm_initial.png`, `LoginForm_withoutProviders.png` |
| `:feature:timeline:ui` | `HomeTimeline_normal.png`, `HomeTimeline_empty.png`, `HomeTimeline_error.png` |
| `:feature:profile:ui` | `ProfileHeader_default.png`, `FollowButton_notFollowing.png`, `FollowButton_following.png`, `FollowButton_pending.png` |

### 日常の検証

```bash
# PR 前などの回帰チェック（baseline との 0px 差を要求）
./gradlew :feature:auth:ui:verifyRoborazziDebug \
          :feature:timeline:ui:verifyRoborazziDebug \
          :feature:profile:ui:verifyRoborazziDebug
```

- 差分が出た場合は `build/outputs/roborazzi/` 以下に actual / diff PNG が生成される
- CI では `.github/workflows/ci.yml` の `android-build` ジョブ内で `verifyRoborazziDebug` を実行し、失敗時は diff を `roborazzi-diffs` artifact としてアップロードする

### UI 変更時のフロー

1. Compose コードを変更する
2. `./gradlew verifyRoborazziDebug` を走らせて差分を確認する
3. 意図した差分であれば `./gradlew recordRoborazziDebug` で baseline を更新
4. 新しい PNG をそのまま commit（レビュアーは PR 上で画像差分を見る）

### 新規画面を追加するとき

1. 対応する `:feature:X:ui` モジュールの `src/androidUnitTest/kotlin/.../XxxScreenshotTest.kt` にテストを追加
2. `ScreenshotScaffold` で `FujuTheme` + `Surface` に包む（既存テストに倣う）
3. Test 専用のダミーデータは同じパッケージの `TestFactories.kt` に追加（prod bundle に漏らさない）
4. `recordRoborazziDebug` → baseline PNG を commit

### 注意点

- **フォント差分**: Roboto などシステムフォントに依存すると OS / emulator で差が出る。最初は default で回し、差分が頻発するようなら `pixelBitConfig = PixelBitConfig.Rgb565` や fixed font bundle を検討する
- **Coil の AsyncImage**: ネットワーク画像は test 時に fetch されない。現在のテストでは `iconUrl = ""` / `bannerUrl = ""` の fallback で描画しているため問題なし。HTTP URL を使う場合は `LocalImageLoader` を test 用の固定 bitmap に差し替える
- **ヘッドレス環境の制約**: Android SDK が無い開発環境では `compileDebugUnitTestKotlin` も実行できない。scaffolding の構文チェックのみ IDE で確認できる

## 参考

- [Roborazzi](https://github.com/takahirom/roborazzi)
- [Turbine](https://github.com/cashapp/turbine)
- [Ktor MockEngine](https://ktor.io/docs/client-testing.html)
