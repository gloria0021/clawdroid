<!--
Sync Impact Report
- Version change: 1.1.0 → 1.1.1
- Modified principles: VII の例だけを改める（名前は IDENTITY.md ではなく agents.update で直す。Gateway が IDENTITY.md も書き直すと実装で確認。マスター指摘 2026-11-24）

以前の履歴:
- Version change: 1.0.0 → 1.1.0
- Modified principles: なし（番号は変えていない）
- Added sections: Core Principles VII「持ち歩くための道具であり、全機能の窓口ではない」、冒頭の説明に位置付けを 1 文、開発フローに仕様の書き方を 1 項目
- Removed sections: なし
- Templates requiring updates: なし（plan の Constitution Check は実行時に本憲章を参照する）。既存の仕様は、001 の「Web UI の設定画面全体は再現しない」をはじめ VII と食い違いなし
- 理由: マスター指定 2026-11-24「ポータブル利用が前提。Gateway の全機能を扱うコンパニオンアプリではない。必要な機能は読み書きもするが、全機能を網羅しない」
- Follow-up TODOs: なし

以前の履歴:
- Version change: (template) → 1.0.0
- Modified principles: すべて新規（テンプレートのプレースホルダーを置換）
- Added sections: Core Principles I–VI, 技術的制約, 開発フローと品質ゲート, Governance
- Removed sections: なし
- Templates requiring updates: なし（plan/spec/tasks テンプレートは実行時に本憲章を参照する）
- Follow-up TODOs: なし
-->

# Clawdroid Constitution

Clawdroid は OpenClaw Gateway を操作する Android 向けコンパニオンアプリである。
ただし、Gateway のすべてを扱う窓口ではなく、持ち歩いて使うための道具である（原則 VII）。
本憲章は開発判断の優先順位と、譲れない規則を定める。見た目の細則は `AGENTS.md` を正とする。

## Core Principles

### I. 動きの心地よさが最優先（NON-NEGOTIABLE）

- 機能・見た目・動きの滑らかさが衝突した場合、滑らかさを取る。優先順位は
  「アニメーション > デザイン性 > 機能の網羅」とする。
- 動きは `MaterialTheme.motionScheme` のトークン（ばね）だけで作る。所要時間・easing を直書きしない。
- 動きの途中でも入力を受け付ける。状態を先に確定させ、見た目があとから追いつく。
- 重い処理（ネットワーク応答の解析・長い一覧の組み立て）は動きと重ねない。
- 毎フレーム変わる値は `graphicsLayer {}` / `drawBehind {}` / `Modifier.offset {}` のラムダ内で読む。
- 理由: 本アプリの価値は「触って気持ちいい OpenClaw」であり、機能数では Web UI に勝てないため。

### II. デザインは AGENTS.md に従う

- 色・文字・余白・形は Material 3 のトークンと `core/designsystem` の定数だけを使う。直書きは禁止。
- UI の骨格は ChatGPT 型のチャットアプリ（左ドロワー＝サイドバー、中央に会話、下部に浮く入力ピル）とする。
- 画面の上に浮く面だけを Haze のガラスで透かす。一覧・本文に透過効果・影・カードを使わない。
- アイコンは Hugeicons Stroke Rounded に統一する。
- `AGENTS.md` と仕様書が食い違う場合は `AGENTS.md` に従い、食い違いを報告する。

### III. Gateway が唯一の正

- セッション・メッセージ・エージェント・モデル・使用量などのデータは OpenClaw Gateway が持つ。
  アプリは表示・操作の窓口であり、独自の正本を持たない（キャッシュと下書きは除く）。
- Gateway のプロトコルは公式資料と実装で確かめたものだけを使い、推測で API を作らない。
- Gateway の再起動など、影響の大きい操作は確認ダイアログを経てから実行する。

### IV. 安全な接続と秘密情報の扱い

- Gateway への接続情報（URL・トークン）は Android Keystore で保護した領域に保存し、ログに出さない。
- 接続は Gateway が許可する経路（ローカルネットワーク / Tailscale 等）に限る。平文の公開経路を推奨しない。
- ファイル編集・ターミナル操作は Gateway の権限に従い、アプリ側で権限を広げない。

### V. 状態を失わない

- 読んでいた位置・入力中の下書き・選択中のエージェントとセッションは、プロセスの終了と構成の変更
  （回転・言語・折りたたみ）を越えて保持する。
- 接続が切れても、表示済みの内容は残し、再接続後に差分で追いつく。

### VI. 簡潔さ

- 数行で書ける処理のために依存を足さない。自作の部品は `AGENTS.md` が許す範囲に限る。
- 1 つの機能は 1 つの縦の切り口（UI → ViewModel → データ層）で完結させ、独立して確かめられるようにする。

### VII. 持ち歩くための道具であり、全機能の窓口ではない

- Clawdroid は、PC から離れているとき（外出先・移動中・手元にスマホだけがあるとき）に OpenClaw を使うためのアプリとする。
  Gateway の全機能を扱うコンパニオンアプリ（Web UI・CLI の代わり）にはしない。
- 機能を入れるかは「持ち歩いているときに必要になるか」で決める。対象は、会話する・状態を確かめる・承認する・
  小さく直す・止めると戸す、のようにその場で済む操作。PC の前で腰を据えて行う作業（初期設定・大きな構成の変更・
  チャンネルやプラグインの管理・作成と削除のような構造の操作）は Web UI・CLI に任せる。
- 入れた機能は、必要なら読み書きまで作る。「スマホだから読むだけ」と一律に削らない。
  ただし、Gateway にあるという理由だけでは機能も設定の項目も足さない。網羅を目標にしない。
- 各仕様は、入れないものを Assumptions に「範囲外（Web UI で行う）」として書く。迷ったら入れずに、必要になってから足す。
- 同じものを直す手段が 2 つある場合は、食い違いが起きない 1 つに絞る（例: エージェントの名前と絵文字は Gateway の `agents.update` で openclaw.json を直す。IDENTITY.md は Gateway が合わせて書き直す）。
- 理由: 全機能を追うと、小さな画面に項目が溢れて原則 I（動きの心地よさ）と II（コンテンツが主役）を壊す。
  Gateway の更新に追いつく保守も終わらなくなる。Web UI が既に全機能を持っており、同じものをもう 1 つ作る価値は小さい。

## 技術的制約

- 言語は Kotlin、UI は Jetpack Compose + Material 3（Expressive の motionScheme を含む）。
- 対応 OS は Android 13（API 33）以上。Haze のガラス効果を全端末で同じに見せるため。
- 画面遷移は Navigation 3、大画面は Material 3 Adaptive の list-detail。
- バージョンは `gradle/libs.versions.toml` を正とする。
- edge-to-edge と予測型「戻る」を前提にする。

## 開発フローと品質ゲート

- 機能は Spec Kit の流れ（specify → clarify → plan → tasks → implement）で進める。
- 仕様を書くとき、各機能が持ち歩きの場面で必要になる理由を User Story の Why に書き、入れないものを Assumptions に書く（原則 VII）。
- 見た目を変えた変更は `AGENTS.md` 第4章のチェックリストを満たすまで完了としない。
- 動きを変えた変更は、アニメーター再生時間スケール 5x での確認と、連打・途中での取り消し・
  予測型「戻る」の途中停止の確認を経る。
- ViewModel とデータ層（Gateway クライアント）には単体テストを書く。

## Governance

- 本憲章は他の開発手順より優先する。ただし見た目の細則は `AGENTS.md` が優先する。
- 改定は、変更理由と影響範囲を本ファイル冒頭の Sync Impact Report に記し、マスターの承認を得て行う。
- バージョンは意味的バージョニング（原則の削除・再定義 = MAJOR、追加・拡張 = MINOR、文言 = PATCH）。
- plan と実装のレビューでは、各原則への適合を確認する。逸脱は plan の Complexity Tracking に理由を書く。

**Version**: 1.1.1 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-24
