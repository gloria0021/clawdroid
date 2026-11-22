# Data Model: Clawdroid

**Date**: 2026-11-22 | **Plan**: [plan.md](./plan.md)

憲章 III（Gateway が唯一の正）に従い、アプリが正本として持つのは「端末の設定」と「下書き・送信待ち・位置」だけ。
それ以外は Gateway から取得した値の写し（キャッシュ）で、いつ捨ててもよい。

保存先の凡例: **[暗号化 DataStore]** = Tink で暗号化した DataStore、**[DataStore]** = 平文の DataStore、
**[Room]** = 端末内の DB、**[メモリ]** = ViewModel / 接続の寿命だけ。

---

## 1. 接続

### GatewayConnection [暗号化 DataStore]

| 項目 | 型 | 規則 |
|---|---|---|
| `url` | String | `ws://` または `wss://`。`ws://` はプライベートアドレス（LAN・Tailscale の 100.64.0.0/10）だけ許す |
| `gatewayToken` | String | 空は不可。画面とログに出さない |
| `deviceToken` | String? | `hello-ok.auth.deviceToken`。承認後に保存。認証の失敗時に消す |
| `deviceTokenScopes` | List<String> | `hello-ok.auth.scopes` |
| `tlsFingerprint` | String? | 使うときだけ |

### DeviceIdentity [暗号化 DataStore]

| 項目 | 型 | 規則 |
|---|---|---|
| `deviceId` | String | 公開鍵の指紋。作り直さない |
| `publicKey` | ByteArray | Ed25519 |
| `privateKey` | ByteArray | Ed25519。Keystore の鍵で暗号化して保存 |

### ConnectionState [メモリ]

```text
Disconnected ─connect→ Connecting ─hello-ok→ Connected
     ▲                    │  ├─PAIRING_REQUIRED→ AwaitingApproval ─device.pair.resolved(approved)→ Connecting
     │                    │  │                                     └─rejected / expired→ Failed(PairingRejected)
     │                    │  └─AUTH_*→ Failed(AuthRejected)   ※自動の再接続を止める
     │                    └─到達できない→ Reconnecting(attempt) ─待ち（段階的に延ばす）→ Connecting
     └─────────── 利用者の「切断」 ─────────── Connected / Reconnecting
Connected ─gateway.restart.request→ Restarting → Reconnecting → Connected
```

- `Failed(AuthRejected)` と `Failed(PairingRejected)` では自動の再接続をしない（FR-004）
- 理由の区別: `AuthRejected`（トークンの拒否）／ `Unreachable`（到達できない）／ `AwaitingApproval`（端末の承認待ち）

---

## 2. エージェントとセッション（Gateway の写し）

### Agent [メモリ ＋ Room のキャッシュ]

| 項目 | 型 | 出典 |
|---|---|---|
| `id` | String | `agents.list` |
| `displayName` | String | 同上 |
| `avatarUrl` / `emoji` | String? | 同上 |
| `defaultModel` | String? | 同上 |
| `kind` | `agent` / `system` | 同上（`agent-kind` の cap） |

### Session [メモリ ＋ Room のキャッシュ]

| 項目 | 型 | 出典 |
|---|---|---|
| `key` | String | `sessions.list` / `sessions.subscribe` |
| `agentId` | String | 同上 |
| `title` | String | 同上 |
| `cwd` | String? | 同上（ヘッダのフォルダ名） |
| `model` / `thinkingLevel` | String? | 同上 |
| `hasActiveRun` | Boolean | 同上（実行中の目印。裏での完了の検知にも使う） |
| `unread` | Boolean | 同上 |
| `archived` / `snoozedUntil` | Boolean / Long? | 同上 |
| `isAutomation` | Boolean | 作られ方の情報（自動化の会話を通常の一覧から分ける。FR-117） |
| `contextUsed` / `contextWindow` | Long? | 同上（コンテキストの使用量） |
| `updatedAt` | Long | 同上 |

- 一覧はエージェントごとに持つ（タブの切り替えで作り直さない。FR-011 の即時の切り替えのため）
- 並び順は `sortBy: "activity"`

### Message [Room のキャッシュ]

| 項目 | 型 | 規則 |
|---|---|---|
| `messageId` | String | `chat.history` の id |
| `sessionKey` | String | |
| `role` | `user` / `assistant` / `tool` | |
| `parts` | List<MessagePart> | 本文（Markdown）・画像・ツールの実行 |
| `createdAt` | Long | |
| `parsed` | 表示用に解析した結果 | ViewModel で一度だけ作る（AGENTS.md 1） |

### MessagePart

- `Text(markdown)`
- `Image(ref)` — 全画面オーバーレイで開く
- `ToolCall(name, description, args, output, status)` — 折りたたんで表示。巨大な出力は先頭だけ

---

## 3. 端末の状態（アプリが正本）

### Draft [Room]

| 項目 | 型 | 規則 |
|---|---|---|
| `sessionKey` | String | 新規セッションは仮の key |
| `text` | String | 入力ごとに保存（間引く） |
| `attachments` | List<Uri> | |

### PendingSend（送信待ちの列）[Room]

| 項目 | 型 | 規則 |
|---|---|---|
| `id` | String | 冪等キー。`chat.send` に渡す |
| `sessionKey` | String | |
| `text` / `attachments` | | |
| `state` | `queued` → `sending` → `sent` / `failed` | `failed` は再試行の操作を出す |
| `origin` | `composer` / `notificationReply` | 通知からの返信は通知に結果を返す（FR-104） |

### ReadingPosition [Room]

| 項目 | 型 |
|---|---|
| `sessionKey` | String |
| `anchorMessageId` | String |
| `offsetPx` | Int |

### UiSelection [DataStore]

| 項目 | 型 |
|---|---|
| `selectedAgentId` | String? |
| `selectedSessionKeyByAgent` | Map<String, String> |

### FileDraft [Room]

| 項目 | 型 | 規則 |
|---|---|---|
| `sessionKey` / `path` | String | |
| `baseHash` | String | 読んだときの `hash`。保存時に `expectedHash` として送り、衝突を検出（FR-052） |
| `text` | String | |

---

## 4. 設定（アプリが正本）[DataStore]

### ThemeSettings

| 項目 | 型 | 初期値 |
|---|---|---|
| `appearance` | `System` / `Light` / `Dark` | `System` |
| `theme` | `OpenClaw` / `Modern` / `Dynamic` / `Custom` | `OpenClaw` |
| `customSeedArgb` | Int? | null（白） |
| `surfaceStyle` | `Glass` / `Blur` | `Glass` |

### NotificationSettings

| 項目 | 型 | 初期値 |
|---|---|---|
| `backgroundConnection` | Boolean | true（FR-120） |
| `notifyReplies` / `notifyApprovals` / `notifyAutomationFailures` | Boolean | true |
| `lockScreenContent` | `FollowSystem` / `Hide` | `FollowSystem`（FR-108） |

- エージェントごとのオン・オフと通知音は Android の通知チャンネルの設定に任せ、アプリでは持たない（FR-102）

---

## 5. その他（Gateway の写し）[メモリ]

### ModelOption — `models.list`

`id`, `label`, `family?`, `thinkingLevels: List<String>`（空ならエフォートの選択を出さない。FR-041）, `defaultThinkingLevel?`

### UsageWindow — `usage.status`

`provider`, `label`（例: 5時間・週）, `usedRatio`, `resetsAt?`, `error?`（取得できない欄だけ示す。FR-045）

### CronJob — `cron.list` / `cron.get`

`id`, `name`, `agentId`, `schedule`（`At(instant)` / `Every(duration)` / `Cron(expr, tz)` / `Other(kind)`。`Other` は表示だけ）, `prompt`, `enabled`, `nextRunAt?`, `lastRun?`

### CronRun — `cron.runs`

`runId`, `startedAt`, `status`（成功・失敗・実行中）, `durationMs?`, `sessionKey?`（実行で生まれた会話）

### TerminalSession — `terminal.*`

`terminalId`, `cwd`, `shell`, `alive`, 再接続のために端末内では `terminalId` だけを保持する

### FileEntry — `sessions.files.list` / `agents.workspace.list`

`path`, `kind`（file / dir）, `size`, `modifiedAt`, `hash?`, `previewable`（テキスト・画像かどうか）
