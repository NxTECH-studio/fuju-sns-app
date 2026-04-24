# ADR 0002: ナビゲーションライブラリの選定

- 状態: Accepted
- 日付: 2026-04-24
- 関連: [ADR 0001 UI 戦略](./0001-ui-strategy.md)

## 背景

`:composeApp` では初期スケルトンとして in-memory の `Navigator`（軽量 back stack）を採用したが、本格的な画面遷移（タブ / deep link / 型安全な引数 / トランジション）には不十分。フェーズ 17 で本格的なナビゲーションを導入する。

UI は Compose Multiplatform（共通）+ iOS SwiftUI ラッパーで、ターゲットは Android / iOS のみ（[ADR 0001](./0001-ui-strategy.md)）。

## 検討した選択肢

### A. androidx.navigation-compose (KMP 2.9.0)

- **開発元**: Google (AndroidX)
- **KMP 対応**: 2.8.0+ で iOS サポート、2.9.0 (Stable 2025) でさらに安定化
- **API**: `NavController` + `NavHost` + `@Serializable` 型安全 route
- **メリット**:
  - Android 開発者にとって学習コストゼロ（Jetpack Compose の標準）
  - `kotlinx.serialization` ベースの型安全 route
  - Deep link のネイティブサポート
  - Compose Navigation の生態系と統合が深い
- **デメリット**:
  - iOS の back gesture / system back の挙動が Android 寄り
  - KMP 対応は比較的新しく、エッジケースで不具合報告あり
  - `rememberNavController` 等の API が暗黙に Android 感

### B. Voyager (cafe.adriel.voyager)

- **開発元**: Adriel Café (community, 活発)
- **KMP 対応**: 設計時点から KMP ネイティブ
- **API**: `Screen` interface + `Navigator { ... }` + `screen.push()`
- **メリット**:
  - KMP イディオムに沿った設計（Screen が `@Composable` を生やすモデル）
  - ScreenModel / Tab / BottomSheet 等のビルトイン機能
  - 学習コストが低い（Screen とナビゲータだけ）
- **デメリット**:
  - Google 純正ではない（将来サポートの懸念）
  - 型安全 route のサポートは緩め（data class ベースだが Serializable 制約なし）
  - コミュニティ規模が AndroidX より小さい

### C. Decompose (com.arkivanov.decompose)

- **開発元**: Arkadii Ivanov (community)
- **KMP 対応**: 主目的が KMP（iOS / Desktop / Web / Android 全部）
- **API**: Component ツリー + ChildStack
- **メリット**:
  - Config change 耐性が最も強い（Component が Compose から独立）
  - Back stack / Child navigation の表現力が高い
  - MVI / Reaktive との親和性
- **デメリット**:
  - API の学習コストが最も高い
  - Compose とは独立した Component 層を設計する必要がある
  - 小〜中規模アプリにはオーバーキル

## 決定

**JetBrains 版 `org.jetbrains.androidx.navigation:navigation-compose` を採用する**（androidx.navigation-compose の KMP fork、Compose Multiplatform 1.8 系と合致する `2.9.0-alpha16` を指定）。

理由:

1. **チームの Android 経験を活かせる**: `../frontend/` は React Router だが、Android 側の主要メンバーはすでに Jetpack Compose Navigation に慣れており、学習コストが低い。
2. **型安全 route**: `kotlinx.serialization` と統合された型安全 route がプロジェクトの他の設計（`@Serializable` DTO）と整合。
3. **Deep link サポート**: OAuth callback (`fuju://auth/callback/{provider}`) のような deep link を宣言的に扱える。
4. **Google 本家のメンテナンス**: Voyager / Decompose は community 依存。長期的な保守を考えると AndroidX のサポートは安心。
5. **iOS 対応の成熟**: 2.9.0 は 2025 年 Stable。Compose Multiplatform 1.8+ と組み合わせて実運用に足る。

Voyager は魅力的だが、型安全と本家サポートを優先する。Decompose は現状の規模ではオーバースペック。

## 実装方針

- `:composeApp/commonMain` に `org.jetbrains.androidx.navigation:navigation-compose` を追加（`libs.jetbrains.navigation.compose`）
- Destination は `@Serializable` sealed interface で定義（`FujuDestination.Login`, `FujuDestination.HomeTimeline`, …）
- `ComposeAppRoot` は認証状態に応じて `NavHost` のスタート destination を切替える
- Deep link は `composable<Route> { navDeepLink { uriPattern = "fuju://…" } }` で宣言的に登録（OAuth callback は `OAuthCallbackParser` 経由に委ね、deep link 宣言は当面見送る）
- 既存の軽量 `Navigator` は削除済み

## 影響

- `composeApp/build.gradle.kts` に `androidx.navigation-compose` を common dep として追加
- `FujuDestination.kt` を `@Serializable` に変更
- `ComposeAppRoot.kt` を NavHost ベースに書き換え
- iOS Simulator での back navigation / deep link を実機で検証する必要あり
- androidx-navigation の KMP artifact が正しく pull できるか確認する（Maven repository に `google()` が必要）

## ロールバック可能性

もし KMP での本家 navigation-compose に重大な不具合があれば、Voyager へ移行できる。移行は:

- Destination 型を Voyager の `Screen` interface に変更
- NavHost を `Navigator { ... }` に置換
- 約 1 日の工数を想定
