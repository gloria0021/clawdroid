# Contract: Gateway の RPC（ターミナルとファイル）

版: OpenClaw 2026.11.6 のスキーマ（`dist/sessions-*.mjs`・`dist/src-*.mjs`・`index-*.d.ts`）で確かめたもの。推測の項目は使わない。

## ファイル

| RPC | 権限 | 送る | 受け取る |
|---|---|---|---|
| `sessions.files.list` | 会話を読める | `sessionKey`, `agentId?`, `path?` | `root?`, `browser { path, parentPath?, entries[{ path, name, kind, size?, updatedAtMs? }], truncated? }` |
| `sessions.files.get` | 会話を読める | `sessionKey`, `agentId?`, `path` | `source?`, `root?`, `file { path, name, size?, updatedAtMs?, content?, hash?, mimeType?, contentEncoding?, previewKind? }` |
| `sessions.files.set` | 会話に書ける・作業フォルダの中 | `sessionKey`, `agentId?`, `path`, `content`, `expectedHash`, `expectedSource?` | get と同じ形（新しい `hash`） |
| `sessions.files.download` | 会話を読める・生きた接続 | `sessionKey`, `agentId?`, `path` | `filename`, `mimeType`, `url`（Gateway からの相対、5 分）, `expiresAt` |
| `agents.workspace.list` | `operator.read` | `agentId`, `path?`, `offset?`, `limit?` | `path`, `parentPath?`, `entries[{ path, name, kind, size?, updatedAtMs? }]`, `totalEntries`, `offset` |
| `agents.workspace.get` | `operator.read` | `agentId`, `path` | `file { path, name, size, updatedAtMs, mimeType, encoding: utf8|base64, content }` |

失敗の扱い:
- `set` の拒否（`expectedHash` の不一致）→ 衝突
- `download` の `session_file_download_unavailable` / HTTP に届かない → ダウンロードできない理由
- `session_file_not_found` → ファイルが消えた

## ターミナル（すべて `operator.admin`。`gateway.terminal.enabled=false` なら全部拒否）

| RPC | 送る | 受け取る |
|---|---|---|
| `terminal.open` | `agentId?`, `cols`, `rows`（`sessionKey` は送らない＝接続だけのもの） | `sessionId`, `agentId`, `shell`, `cwd`, `confined`, `title?` |
| `terminal.input` | `sessionId`, `data` | — |
| `terminal.resize` | `sessionId`, `cols`, `rows` | — |
| `terminal.list` | — | つなぎ直せる PTY の一覧（`sessionId`, `cwd`, `attached`, `owner`, `createdAtMs` …） |
| `terminal.attach` | `sessionId` | open と同じ ＋ `buffer`, `seq?` |
| `terminal.close` | `sessionId` | — |

| イベント | 中身 |
|---|---|
| `terminal.data` | `sessionId`, `seq`, `data` |
| `terminal.exit` | `sessionId`, `exitCode?`, `signal?`, `reason?`（`process_exit` / `closed` / `disconnected` / `detached` / `error`）, `error?` |
