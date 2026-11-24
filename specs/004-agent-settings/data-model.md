# Data Model: エージェントの設定

**Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

アプリが持つ正本は下書きだけ。ほかはすべて Gateway の値を写したもの（憲章 III）。

## Agent（既存に項目を足す）

`core/data/Models.kt`。`agents.list` から作る。

| 項目 | 型 | 説明 |
|---|---|---|
| id・name・emoji・avatarUrl・defaultModel・isSystem | 既存 | 変えない |
| createdVia | `String?` | `operator` / `agent` / `template`。無ければ null |
| creatorAgentId | `String?` | 作ったエージェント |
| createdAt | `Long?` | ミリ秒 |

## FileRoot.AgentFile（新規、`core/data/FileModels.kt`）

```text
FileRoot.AgentFile(agentId: String)
```

- 一覧は平ら（フォルダが無い）。`path` = ファイルの名前
- 下書きの鍵: `agent:<agentId>:<name>`

## AgentFileEntry（新規）

`agents.files.list` の 1 行。

| 項目 | 型 | 説明・検証 |
|---|---|---|
| name | `String` | 例: `AGENTS.md`。一覧の key |
| role | `AgentFileRole?` | 名前から決める役割（下表）。知らない名前は null |
| missing | `Boolean` | 未作成 |
| expectedAbsent | `Boolean` | 無いのが普通のファイル（USER.md・MEMORY.md など）。「未作成」を警告の色にしない |
| size | `Long?` | バイト |
| updatedAt | `Long?` | ミリ秒 |

並び順: 存在するもの → 未作成。その中は下表の順、知らない名前は最後に名前順。

| AgentFileRole | 名前 | 説明（strings.xml） |
|---|---|---|
| INSTRUCTIONS | AGENTS.md | 振る舞いの指示 |
| SOUL | SOUL.md | 人格と口調 |
| IDENTITY | IDENTITY.md | 名前と雰囲気 |
| USER | USER.md | 利用者について |
| HEARTBEAT | HEARTBEAT.md | 定期の見回り |
| MEMORY | MEMORY.md | 長く覚えておくこと |
| BOOTSTRAP | BOOTSTRAP.md | 初回の儀式 |

## OpenedFile（既存に項目を足す）

| 項目 | 型 | 説明 |
|---|---|---|
| workspace | `String?` | `agents.files.get` の `workspace`。保存の `expectedWorkspace` に返す。それ以外のルートは null |
| creating | `Boolean` | 未作成のファイルを開いた（保存は `expectedMissing: true`） |

`editable` の条件を広げる:

- `FileRoot.Session`: 従来どおり
- `FileRoot.AgentFile`: 文字のファイルで、`size` が 2 MiB 以下で、`hash` があるか `creating`

## OpenedFileController（新規、`feature/files/OpenedFileController.kt`）

`FilesViewModel` から切り出す状態の移り変わり。状態の型は既存の `OpenedState` をそのまま使う。

```text
Loading ──取得──▶ Ready(読む) ──編集──▶ Ready(編集中) ──保存──▶ Ready(saving)
   │                  ▲                     │                     │
   └──失敗──▶ Failed   └──読み直す/保存できた─┴──衝突──▶ Ready(conflict)
                                              └──閉じる(変更あり)──▶ 下書きに保存
```

- 入力: `CoroutineScope`・`FileSource`・`FileDraftDao`・下書きの鍵の作り方
- 出力: `StateFlow<OpenedState?>` と、保存できた・失敗したのイベント（Snackbar 用）
- `FilesViewModel` と `AgentFileViewModel` が持つ

## SkillStatus（新規、`core/data/Skills.kt`）

`skills.status` の `skills[]` の 1 件。

| 項目 | 型 | 説明 |
|---|---|---|
| skillKey | `String` | 有効・無効の切り替えと一覧の key |
| name | `String` | 表示名 |
| description | `String?` | |
| emoji | `String?` | |
| filePath | `String?` | SKILL.md の場所（`skills.files.get` に渡す） |
| source | `String?` | 出どころ（同梱・ワークスペース・管理など） |
| state | `SkillState` | `ENABLED`（使える）/ `UNMET`（要件が足りない）/ `DISABLED`（無効） |
| missing | `SkillMissing` | `bins`・`anyBins`・`env`・`config`・`os`（いずれも `List<String>`） |
| blockedByAllowlist | `Boolean` | 同梱スキルの許可リストで止められている |
| blockedByAgentFilter | `Boolean` | エージェントのスキルの絞り込みで外れている |

`state` の決め方: `disabled` → DISABLED、`eligible` → ENABLED、それ以外 → UNMET。
並び順: ENABLED → UNMET → DISABLED、その中は名前順。

## AgentSettingsUiState（新規）

| 項目 | 型 | 説明 |
|---|---|---|
| agent | `Agent?` | 一覧から見つからなければ null（失敗の表示） |
| files | `Load<List<AgentFileEntry>>` | Loading / Loaded / Failed(unsupported: Boolean) |
| skills | `Load<List<SkillStatus>>` | 同上 |
| pendingToggles | `Map<String, Boolean>` | 切り替え中のスキルの、先に見せている値 |
| skillQuery | `String` | 絞り込み（20 件を超えるときだけ出す） |

`unsupported` は Gateway が `METHOD_NOT_FOUND` 等で手段を持たないとき（spec FR-041）。
