# Implementation Plan: 通知（常駐の接続で受け取る）

**Branch**: `006-notifications`（ブランチは切らず main で進める） | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-notifications/spec.md`

## Summary

アプリの Gateway への接続（`GatewayClient`。今はアプリのプロセスが生きているあいだ保たれる）を、常駐の通知を伴うフォアグラウンドサービス
（種類は connectedDevice。公式アプリと同じ）でプロセスごと生かし続ける。通知は、すでにアプリ全体で受けている出来事から端末内で作る。

- 応答の完了: `sessions.subscribe`（既存）の `sessions.changed` で、会話の行が「実行中 → 止まった」に変わったのを見つけ、`chat.history` の最後の応答を読んで通知する
- 承認待ち: 既存の `ApprovalsRepository`（`exec.approval.*` / `plugin.approval.*` の requested / resolved）の一覧の増減をそのまま通知に写す。通知のボタンは同じ `resolve()` を呼ぶ
- 自動化の失敗: `cron` の出来事の `action = finished` で、結果が失敗のものを通知する

裏にあるあいだは、開いている会話の文字ごとの流れ（`sessions.messages.subscribe`）を止め、戻ったら取り直す。調査は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001〜005 と同じ）

**Primary Dependencies**: 既存のみ（Compose・M3・Navigation 3・OkHttp・DataStore・`androidx.core` の `NotificationCompat`・
導入済みの `lifecycle-process`（`ProcessLifecycleOwner`）と `lifecycle-service`（`LifecycleService`））。新しい依存なし

**Storage**: 既存の設定（`SettingsStore` の `backgroundConnection`・`notifyReplies`・`notifyApprovals`・`notifyAutomationFailures`）。`lockScreenContent` は 2026-11-26 に削除（端末の設定に任せる）。
通知済みの応答の識別子は、つなぎ直しの重複を防ぐためにメモリと SharedPreferences（会話ごとに最後に通知した応答の時刻）に持つ

**Testing**: JUnit 4 ＋ 既存の偽の Gateway。完了の見つけ方（行の移り変わり）、通知の文の作り方（Markdown の記号を除く・冒頭）、
画面で開いている会話を外す判定、自動化の失敗の判定、承認の一覧の差分から出す・消す通知の決め方

**Target Platform**: Android 13（API 33）以上

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: 応答の完了から通知まで 5 秒以内（SC-001）。待ち受けの電池は 1 時間あたり 2% 以下（SC-005）

**Constraints**: 外部の Push・中継サーバーを使わない（spec Assumptions）。承認の決定はロック中に送らない（FR-024）。
Gateway の生存確認（25 秒ごとの ping）はアプリ側で変えられない

**Scale/Scope**: サービス 1・通知の部品 1 式・設定画面の 1 セクション。ストーリー 4 本、要件 FR-001〜035

## Constitution Check

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさ | 適合 | 画面に動きを足さない。裏の処理は画面の動きと重ならない |
| II. AGENTS.md | 適合 | 設定は `ListItem` ＋ `Switch`（2.7）。通知のアイコンは Hugeicons を VectorDrawable にしたもの |
| III. Gateway が唯一の正 | 適合 | 通知の内容は Gateway の出来事と `chat.history` から作る。承認の決定は Gateway に送るだけ |
| IV. 安全 | 適合 | 承認のボタンはロックの解除を求める（`setAuthenticationRequired`）。ロック画面の表示は端末の設定に従う（2026-11-26 にアプリの項目を削除）。接続情報はこれまでどおり Keystore |
| V. 状態を失わない | 適合 | つなぎ直したあとに、切れていたあいだの完了をまとめて通知する |
| VI. 簡潔さ | 適合 | 新しい接続を作らない。既存の `GatewayClient`・`ApprovalsRepository`・`sessions.subscribe` を使い回す |
| VII. 持ち歩く道具 | 適合 | 通知と承認は持ち歩きの中心の用途。返信・おやすみ時間などは範囲外 |

設計後の再確認: 変わらず。

## Project Structure

### Documentation (this feature)

```text
specs/006-notifications/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── gateway-events.md     # 使う出来事と RPC
│   └── notifications.md      # チャンネル・通知の形・ボタン・タップの行き先
└── tasks.md                  # /speckit-tasks で作る
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                         # ConnectionService（connectedDevice）・BOOT_COMPLETED・通知のボタンの受け手
└── kotlin/jp/miyavi/android/clawdroid/
    ├── ClawdroidApp.kt                         # 起動時と前後の切り替えで常駐を始める・止める
    ├── core/notify/
    │   ├── ConnectionService.kt                # 新規: 常駐（フォアグラウンドサービス）と常駐の通知
    │   ├── NotificationCenter.kt               # 新規: チャンネル・各通知の組み立てと出し消し
    │   ├── ReplyWatcher.kt                     # 新規: sessions.changed から応答の完了を見つける（純粋な判定は ReplyCompletion）
    │   ├── AutomationWatcher.kt                # 新規: cron の出来事から失敗を見つける
    │   ├── ApprovalNotifier.kt                 # 新規: 承認の一覧の差分を通知に写す
    │   ├── ApprovalActionReceiver.kt           # 新規: 通知のボタン（許可・拒否）を受けて resolve する
    │   └── BootReceiver.kt                     # 新規: 端末の起動で常駐を再開
    ├── core/data/ChatRepository.kt             # 裏にあるあいだ messages の購読を止め、戻ったら取り直す
    ├── core/gateway/GatewayClient.kt           # caps に chat-only-assistant-text を足す（R3）
    ├── feature/settings/SettingsScreen.kt      # 通知のセクション
    └── MainActivity.kt                         # 通知から開く行き先（会話・承認・自動化）
app/src/test/kotlin/jp/miyavi/android/clawdroid/core/notify/
    ├── ReplyCompletionTest.kt
    ├── NotificationTextTest.kt
    ├── AutomationFailureTest.kt
    └── ApprovalNotifierTest.kt
```

**Structure Decision**: 通知はアプリ全体の仕組みなので `core/notify` に置く。データは既存の Repository と `GatewayClient.events` から受け、画面からは独立させる。

## Complexity Tracking

| 逸脱 | なぜ必要か | より単純な案を退けた理由 |
|---|---|---|
| なし | | |
