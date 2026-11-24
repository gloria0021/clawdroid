# Data Model: 通知（常駐の接続で受け取る）

## SessionRunState（ReplyWatcher が会話ごとに覚える。メモリ）

| フィールド | 型 | 備考 |
|---|---|---|
| `sessionKey` | String | |
| `agentId` | String? | |
| `running` | Boolean | `hasActiveRun` または `status` が running / queued |
| `lastNotifiedMessageId` | String? | 前に通知した応答。SharedPreferences にも保つ（つなぎ直し・プロセスの再起動で重ねて出さない） |

**移り変わり**: `running = true` → `false` で完了。初めて見た行（前の状態が無い）では通知しない（起動直後の取り違えを防ぐ）

## ReplyNotification

| フィールド | 型 | 備考 |
|---|---|---|
| `sessionKey` / `agentId` | String | 通知の id は `sessionKey` から作る（会話ごとに 1 つ） |
| `agentName` / `sessionTitle` | String | 無ければ「エージェント」「新しいチャット」 |
| `messageId` | String | 重複の判定 |
| `text` | String | Markdown の記号を除いた冒頭（300 文字まで）。画像だけなら「画像」 |
| `at` | Long | |

## ApprovalNotification

`PendingApproval`（既存）をそのまま使う。通知の id は `ApprovalKey` から作る。本文は `detail`（コマンド）・`target`（作業フォルダなど）・`warning`・エージェント・会話。
ボタンは `decisions` の順に 1 回だけ許可・常に許可・拒否。

## AutomationFailure

| フィールド | 型 | 備考 |
|---|---|---|
| `jobId` / `name` / `agentId` | String | |
| `error` | String? | 1 行目だけ |
| `runAt` | Long | 同じ実行を重ねて出さない |

## NotificationSettings（既存の SettingsStore の項目）

| 項目 | 既定 |
|---|---|
| `backgroundConnection` | true |
| `notifyReplies` / `notifyApprovals` / `notifyAutomationFailures` | true |
| `lockScreenContent` | 端末の設定に従う |

## ConnectionServiceState

`Stopped(reason)`（オフ・許可が無い・接続情報が無い）/ `Connected` / `Reconnecting`。常駐の通知と設定画面に出す。
