# iosApp

SwiftUI の薄い iOS ラッパー。Kotlin 側の `composeApp` モジュールが出力する
`ComposeApp.framework` をリンクして、`ComposeUIViewController` をホストする。

## セットアップ

本リポジトリには `iosApp.xcodeproj` を含めていない（手書き pbxproj の保守は困難
なため）。以下のいずれかで Xcode プロジェクトを生成する。

### Option A: Android Studio Panda3 の KMP Plugin（推奨）

1. Android Studio で本リポジトリを開く。
2. `File → New → Project from Version Control...` もしくは `Open...` で
   `/home/sheep/dev/fuju/app/` を選択。
3. 初回 Gradle sync 完了後、`Tools → Kotlin → KMP → Generate iOS Xcode Project`。
4. 生成された `iosApp.xcodeproj` が `iosApp/` 配下に作られる。

### Option B: Kotlin JetBrains テンプレートから手動生成

1. https://kmp.jetbrains.com/ の iOS SwiftUI テンプレートを参照する。
2. 生成後、`iosApp/iOSApp.swift` と `Info.plist`（本リポジトリの内容）で置き換える。
3. Framework Search Paths に `$(SRCROOT)/../composeApp/build/bin/iosSimulatorArm64/...`
   など XCFramework のパスを追加する。

## ビルド手順（要 macOS + Xcode）

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
cd iosApp && xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16'
```

## OAuth callback

`Info.plist` の `CFBundleURLTypes` で `fuju://` スキームを宣言している。
Social 認証からの戻り URL を `.onOpenURL` で受け、Kotlin 側の
`AuthStateMachine.completeSocialCallback(...)` に流す実装を
フェーズ 4 の `19-wire-ios-app` ステップで追加する。
