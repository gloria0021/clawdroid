# Contract: Gateway の呼び出し（004）

確かめた元: `reference/openclaw/packages/gateway-protocol/src/schema/agents-files.ts`・`agents-models-skills.ts`、
`docs/gateway/protocol/*.md`、`dist/status-*.mjs`。ここに無い呼び出しは使わない（憲章 III）。

## agents.files.list（operator.read）

```jsonc
// params
{ "agentId": "architect" }
// result
{ "agentId": "architect", "workspace": "<opaque>",
  "files": [ { "name": "AGENTS.md", "path": "...", "missing": false, "expectedAbsent": false,
               "size": 1234, "updatedAtMs": 1764000000000, "hash": "<sha256>" } ] }
```

- `path`・`workspace` は表示しない（ホストのパスを画面に出さない）。`workspace` は保存の条件にだけ使う

## agents.files.get（operator.read）

```jsonc
{ "agentId": "architect", "name": "AGENTS.md" }
// result
{ "agentId": "architect", "workspace": "<opaque>",
  "file": { "name": "AGENTS.md", "path": "...", "missing": false, "size": 1234, "updatedAtMs": 0, "hash": "<sha256>", "content": "..." } }
```

- `missing: true` なら `content` は無い → 空の内容で `creating = true` として開く

## agents.files.set（operator.admin 相当の書き込み）

```jsonc
// 既存のファイル
{ "agentId": "architect", "name": "AGENTS.md", "content": "...", "expectedHash": "<読んだときの hash>", "expectedWorkspace": "<読んだときの workspace>" }
// 未作成のファイル
{ "agentId": "architect", "name": "USER.md", "content": "...", "expectedMissing": true, "expectedWorkspace": "<...>" }
// 上書きを選んだとき（衝突のダイアログ）: expectedHash を details.currentHash にして送り直す
// result
{ "ok": true, "agentId": "architect", "workspace": "<...>", "file": { ...AgentsFileEntry, "hash": "<新しい hash>" } }
```

| エラー | 判定 | アプリの扱い |
|---|---|---|
| `INVALID_REQUEST` + `details.type == "agent_file_conflict"` | 衝突 | `SaveResult.Conflict`（`details.currentHash` があれば上書きに使う。無ければ読み直してから上書き） |
| 2 MiB を超える | 送る前にアプリで止める | 読むだけで開いている |
| そのほか | 失敗 | `SaveResult.Failed(message)` → Snackbar に理由と再試行 |

## skills.status（operator.read）

```jsonc
{ "agentId": "architect" }
// result（スキーマは型を定めていない。実装の出力に合わせ、知らない項目は無視する）
{ "workspaceDir": "...", "managedSkillsDir": "...",
  "skills": [ { "name": "weather", "description": "...", "emoji": "🌤", "skillKey": "weather",
                "filePath": ".../SKILL.md", "source": "openclaw-bundled", "bundled": true,
                "eligible": false, "disabled": false, "blockedByAllowlist": false, "blockedByAgentFilter": false,
                "platformIncompatible": false,
                "missing": { "bins": ["curl"], "anyBins": [], "env": [], "config": [], "os": [] } } ] }
```

- `workspaceDir`・`managedSkillsDir`・`baseDir` は表示しない
- `primaryEnv`・`install`・`configChecks` は v1 では使わない（導入は範囲外）

## skills.update — 設定のモード（operator.admin）

```jsonc
{ "skillKey": "weather", "enabled": false }
```

- `apiKey`・`env` は送らない（秘密の値をアプリで扱わない）
- 成功したら `skills.status` を読み直す

## skills.files.get（operator.read）

```jsonc
{ "agentId": "architect", "path": "<skills.status の filePath>" }
// result
{ "path": "...", "name": "weather", "content": "---\nname: weather\n..." }
```

- 失敗（`INVALID_REQUEST`: 大きすぎる・見つからない）は、シートの中に理由を示す

## config.get（概要。FR-010、research R11）

```jsonc
// params
{}
// result（秘密は Gateway が伏せる）
{ "hash": "...", "config": {
    "agents": { "defaults": { "model": "...", "thinkingDefault": "low", "workspace": "/..." },
                "entries": { "architect": { "model": "...", "thinkingDefault": "medium", "workspace": "/.../workspace-architect",
                  "tools": { "profile": "full", "allow": ["group:fs"], "alsoAllow": [], "deny": ["bundle-mcp"],
                             "exec": { "mode": "auto" }, "elevated": { "enabled": false }, "fs": { "workspaceOnly": false } } } } },
    "tools": { "profile": "coding", "web": { "search": { "enabled": true, "provider": "..." } } } } }
```

- 読むのは上の項目だけ。ホストのパスはワークスペースの名前（最後の部分）だけを画面に出す
- 書き込み（`config.patch` など）はしない

## agents.list（既存、読む項目を足す）

- 足す項目: `createdVia`・`creatorAgentId`・`createdAt`（いずれも任意）

## 手段が無いとき

`METHOD_NOT_FOUND`（古い Gateway）を受けたら、そのタブを「この Gateway では使えません」にする。ほかのタブは使えるまま（spec FR-041）。
