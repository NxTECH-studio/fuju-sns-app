このプロジェクトは次のプロジェクトを統合し、KMPにトランスパイルをしたものです。
プロジェクト： https://github.com/NxTECH-studio/fuju-sns-frontend (../frontend/)
(Auth-Component(fuju-auth-react): (../auth-component))

なお、バックエンド（https://github.com/NxTECH-studio/fuju-sns-backend (../backend/)) や統合認証基盤(AuthCore) (https://github.com/NxTECH-studio/fuju-system-authentication (../auth))も参考にしてください。

## 開発環境
Android Studio Panda3

## ロジック
Kotlin Maltiplatform

## UI
*要検討*: ComposeMaltiplatform or Jetpack + Swift UI(jetpackで記述→debin等でActionsでSwift UIにtranspile)

## その他
- フロントエンドのdevelopブランチを参照すること
- Actionsの整備をすること（lint, build check)
- Android Studioで使えるようにすること（プロジェクトを読み込んで適切に検証・動作確認ができること）


