# ADR 0001: UI 戦略 — Compose Multiplatform + iOS SwiftUI ラッパー

- ステータス: **Accepted**
- 日付: 2026-04-24
- 決定者: Fuju チーム
- 関連ドキュメント: `docs/tasks/migrate-fuju-sns-to-kmp.md`

## 文脈

Fuju SNS の iOS / Android 対応にあたり、既存の React + TypeScript 実装
（`fuju-sns-frontend` / `fuju-auth-react`）のロジックを Kotlin Multiplatform (KMP)
に移植する。UI 層の戦略として、以下の候補を比較検討した。

| # | 戦略 | 共通化率 | メンテ負荷 | iOS ネイティブ体験 |
|---|------|---------|-----------|-------------------|
| A | Compose Multiplatform (Android + iOS UIKit host) | 高 (90%+) | 低 | Material3 主体。iOS HIG からは乖離するが慣用に収まる |
| B | Jetpack Compose (Android) + SwiftUI (iOS) 手書き | 中 | 高 (UI は実質二重) | iOS は完全ネイティブ |
| C | Jetpack Compose + SwiftUI transpile (dbin 等) | 中〜高 | 中 (ツール依存) | iOS は SwiftUI。ツール成熟度が鍵 |
| D | React Native / Flutter | 高 | 中 | ロジック KMP を生かせない |

## 決定

**A: Compose Multiplatform を共通 UI レイヤとし、iOS では SwiftUI から
`ComposeUIViewController` をホストする** を採用する。

具体的には以下のとおり。

1. `:composeApp` モジュールで Compose Multiplatform を用いた UI を一元実装する。
2. Android (`:androidApp`) は `MainActivity` で `ComposeAppRoot()` を直接呼ぶ。
3. iOS (`iosApp/`) は SwiftUI アプリとして構成し、
   ルート画面で `ComposeUIViewController` を表示する。
4. iOS 固有の画面（カメラ、共有シート、StoreKit など）が必要になった場合のみ、
   その画面だけ SwiftUI で個別実装し、Kotlin 側の `StateFlow` を
   `@Published` に橋渡しするアダプタを `iosApp/` に書く。

## 理由

- **共通化率の最大化**: React 版の state / hooks / ルーティング構造を単一の
  Kotlin コードベースに移せる。UI も Compose で統一できれば、仕様追加時の
  実装コストが Android / iOS で 1x に収まる。
- **Compose Multiplatform iOS の成熟度**: 2025-2026 にかけて stable 扱いとなり、
  `ComposeUIViewController` 経由の統合は JetBrains 公式テンプレートに含まれる。
- **移植のしやすさ**: React の関数コンポーネント + hooks の書き味は Compose に
  最も近く、`useState` → `remember`、`useEffect` → `LaunchedEffect`、
  `useSyncExternalStore` → `collectAsState()` と 1:1 で対応しやすい。
- **iOS ネイティブ API への退避路**: 全画面 SwiftUI を捨てるのではなく、
  必要に応じて SwiftUI を組み合わせることで、Haptics / Camera / Share /
  Universal Link などのプラットフォーム固有機能を通常どおり呼び出せる。

## 却下案

- **B（Jetpack + SwiftUI 手書き）**: UI ロジックの二重実装が避けられず、
  バグが OS 間で非対称になる。仕様の三重管理（Web / iOS / Android）を解消
  する本プロジェクトの目的に反する。
- **C（Compose → SwiftUI transpile）**: transpile の対応範囲が Compose の
  Material3 / gesture / 複雑な state すべてに追いつかず、Compose 更新に
  対応が追従するかの保守リスクが大きい。将来、成熟した transpile 手段が
  現れたら再評価する。
- **D（React Native / Flutter）**: ロジックを KMP に集約する本プロジェクトの
  前提を捨てる。AuthCore Cookie + Bearer の精緻な扱いを JS ランタイムで
  再実装する負担に見合わない。

## 結果と影響

- `:core:ui` に Compose ベースのデザインシステムを置き、`:feature:*:ui` は
  その上で個別画面を実装する。
- iOS 固有の機能追加時は `:composeApp` の expect / actual で薄いアダプタを
  作り、`iosApp/` 側で SwiftUI を交えて実装する。
- Material3 の iOS 表現が HIG から乖離する部分（Navigation の戻るジェスチャ、
  SafeArea、フォント）は、Compose 側の設定と SwiftUI 側のコンテナ設定で
  可能な限り吸収する。

## メモ

- Compose Multiplatform が iOS で提供する機能（キーボード回避、Safe area、
  Navigation）は `compose-multiplatform` 1.8 以降で安定しており、本プロジェクト
  では `libs.versions.toml` に 1.8.2 を固定する。
- 共通 UI に収まらない画面が将来的に 3 割を超える兆候が出た場合、B 案への
  戻し（SwiftUI 全面移行）を再検討する。
