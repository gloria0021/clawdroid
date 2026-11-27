# Contract: Gateway RPC（SumahoClaw が使う範囲）

**Protocol**: OpenClaw Gateway WebSocket protocol v4（OpenClaw 2026.11.6）
**正**: `reference/openclaw/packages/gateway-protocol/src/schema/` と `reference/openclaw/docs/gateway/protocol/`。
ここは「どれを、何のために、どの scope で使うか」の一覧。引数と戻り値の詳細はスキーマを見る（推測で項目を作らない）。

## フレーム

| 種類 | 形 |
|---|---|
| 要求 | `{ "type": "req", "id", "method", "params" }` |
| 応答 | `{ "type": "res", "id", "ok", "payload" \| "error" }` |
| イベント | `{ "type": "event", "event", "payload" }` |

## 接続

| 手順 | 内容 |
|---|---|
| 1 | Gateway → `event connect.challenge { nonce, ts }` |
| 2 | アプリ → `req connect`: `minProtocol/maxProtocol = 4`、`role = "operator"`、`scopes = [operator.read, operator.write, operator.admin, operator.approvals]`、`auth.token`（保存した `deviceToken` があればそれ、なければゲートウェイトークン）、`device { id, publicKey, signature, signedAt = ts, nonce }`（v3 の署名）、`caps = [hello-health-on-demand, health-summary, usage-refreshing, agent-kind]` |
| 3a | 成功 → `hello-ok`。`auth.deviceToken` / `auth.scopes` を保存。`policy.tickIntervalMs` を記録 |
| 3b | `PAIRING_REQUIRED`（`waitForResolution: true`）→ ソケットを保ち `device.pair.resolved` を待つ |
| 3c | `AUTH_TOKEN_MISMATCH` など → `error.details.recommendedNextStep` に従う。自動の再接続を止める場合あり |

生存確認: Gateway が 25 秒ごとに ping、30 秒ごとに `tick`。アプリは pong を返すだけ。`tick` が `tickIntervalMs × 2` 届かなければ閉じて再接続する。

## RPC

| 機能（spec） | Method | Scope | 用途 |
|---|---|---|---|
| US1 エージェント | `agents.list` | read | タブの一覧 |
| US1 セッション一覧 | `sessions.subscribe` | read | 初回の一覧＋`sessions.changed` の購読（`sortBy: "activity"`） |
| | `sessions.list` | read | 再取得・ページ送り |
| | `sessions.patch` | dynamic | 改名（`label`）・アーカイブ（`archived` ＋ `expectedSessionId`）・ピン留め（`pinned`）・既読 |
| | `sessions.delete` | dynamic | 削除（確認付き） |
| US1 セッションのメニュー | `sessions.diff` | read | 変更量（`scope: "all"`、`poll: true`。メニューの合計は `summaryOnly: true`、集計中は取り直す） |
| US1 補完 | `commands.list` | read | `/` のコマンドと `$` のスキルの候補 |
| US1 会話 | `chat.startup` | read | 会話を開く（モデルの一覧の準備を待たない） |
| | `chat.history` | read | 履歴（遡って読み込む） |
| | `chat.message.get` | read | 1 件の全文（省略された長い応答） |
| | `sessions.messages.subscribe` / `unsubscribe` | read | 開いている会話のイベント（`includeApprovals: true`） |
| | `chat.send` | write | 送信（冪等キー付き。添付は `policy.attachments` の上限内） |
| | `chat.abort` | write | 停止（`sessionId` ＋ `runId`） |
| US1 承認 | `approval.get` / `approval.resolve`、`exec.approval.*`、`plugin.approval.*` | approvals | 承認・拒否 |
| US3 新規セッション | `sessions.create` | dynamic | `agentId`・`cwd`・最初のメッセージ |
| | `projects.list` | read | 直近のフォルダの候補（`includeObserved: true` のチェックアウト。これまでのセッションの作業フォルダと合わせて 5 つ） |
| | `fs.listDir` | dynamic | フォルダを辿る（返ったパスをそのまま渡す） |
| US3 並行開発 | `sessions.create` | dynamic | `parentSessionKey`・`cwd`・`worktree: true`・`worktreeName`（空なら Gateway が決め、重なれば番号を付ける）・`worktreeBaseRef`・`fork`。`label` は送らない（同じ名前を断られるため） |
| | `worktrees.branches` | write | 元にするブランチの候補（`repoRoot` に作業フォルダ。`headBranch` を初期値に。ワークスペースの外は admin が要る） |
| US4 モデル | `models.list` | read | モデルとエフォートの選択肢 |
| | `sessions.patch` | dynamic | `model` / `thinkingLevel` |
| | `sessions.patch` | dynamic | `permissionMode`（権限） |
| US4 使用量 | `models.authStatus` / `usage.status` | read | プロバイダの使用枠（`models.authStatus` を優先） |
| | `sessions.usage` | read | セッションの使用量とコンテキストの内訳（`contextWeight`） |
| US5 ファイル | `sessions.files.list` / `get` / `set` | read / write | 作業フォルダ（`set` は `expectedHash` で衝突検出） |
| | `sessions.files.download` | read | ダウンロードの短命のリンク（接続に紐付く。切れたら取り直して Range で続きから） |
| | `agents.workspace.list` / `get` | read | エージェントのワークスペース（読み取り専用） |
| US6 ターミナル | `terminal.open` / `input` / `resize` / `close` / `list` / `attach` | admin | PTY |
| US7 再起動 | `gateway.restart.request` | admin | 再起動の指示 |
| US8 自動化 | `cron.list` / `get` / `status` | read | 一覧・詳細 |
| | `cron.update` / `remove` / `run` | write | 編集・有効と無効・削除・今すぐ実行（`run` は `waitTimeoutMs` で結果を待てる） |
| | `cron.add` | write | 作成（未実装） |
| | `cron.runs` | read | 実行履歴（`view: "summary"`） |

## イベント

| Event | 受ける時 | 用途 |
|---|---|---|
| `tick` | 常に | 生存確認 |
| `sessions.changed` | 常に（裏でも） | 一覧の更新。`hasActiveRun` の変化で応答の完了を検知し通知（FR-100） |
| `chat` / `agent` | 会話を開いているときだけ | 応答の逐次表示・ツールの実行 |
| `session.approval`、`exec.approval.requested` / `resolved`、`plugin.approval.requested` / `resolved` | 常に | 承認待ちの表示と通知（FR-106） |
| `cron.inventory.changed` | 自動化の画面を開いているとき | 一覧の取り直し |
| `terminal.data` / `terminal.exit` | ターミナルを開いているとき | 出力 |
| `device.pair.resolved` | 承認待ちのとき | 承認の結果 |
| `config.changed`、`chat.metadata.changed` | 常に | モデル一覧などの取り直し |

## 未確認（実装の最初に確かめる）

- `sessions.changed` の `hasActiveRun` の変化だけで、裏での「応答の完了」を取りこぼしなく検知できるか（quickstart の検証 3）
- 共有トークンで要求した `operator.admin` が、承認後の `deviceToken` でも維持されるか
- `gateway.restart.request` の引数（`target` など）と、再起動中の切断の見え方
