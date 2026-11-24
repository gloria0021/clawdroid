# Contract: 画面と部品（004）

見た目と動きは AGENTS.md が正。ここは本機能での使い分けだけを決める。

## 入口

| 場所 | 部品 | 内容 |
|---|---|---|
| サイドパネルの看板 | 名前の `Text` を `Row` に入れ、右に `IconButton`（`ic_huge_edit`、48dp、`agent_settings_open_cd`） | 押すと `AgentSettingsRoute(currentAgent.id)` を開き、パネルを閉じる。名前は 2 行まで省略記号（既存のまま）、アイコンは名前の 1 行目の高さにそろえる |
| 会話の応答のアバター | `AgentAvatar` に `clickable(onClickLabel = agent_settings_open_cd)` | そのエージェントの設定を開く。生成中の波の表示は変えない |

## AgentSettingsRoute(agentId) — 新しい画面

- 共通の TopAppBar（ガラス 20%）。タイトルはエージェントの名前（`titleMedium`、2 行まで）。左に戻る
- 下部に浮くガラスの `PillTabs`: 概要（`ic_huge_info`）/ ファイル / スキル。アイコンは既存の Hugeicons から選び、無ければ足す
- タブの中身は選んだものだけを描き、新しい側を `defaultEffectsSpec()` のフェードで入れる

### 概要

- 上部: `AgentAvatar`（大）・名前（`headlineSmall`）・絵文字
- 名前の下に「名前と雰囲気を直す」（`TextButton`）→ `AgentFileRoute(agentId, "IDENTITY.md")`
- `ListItem` を縦に並べる: 既定のモデル / 作られた経緯（`labelMedium`、無ければ行ごと出さない）/ ファイル N 件（押すとファイルのタブ）/ スキル 使える N / 全体 M（押すとスキルのタブ）/ ワークスペースを開く（既存の `FilesRoute` をワークスペースのルートで）
- 区切り線は使わず、余白で分ける

### ファイル

- `LazyColumn` の `ListItem`、key = name。タイトル = 名前（`titleMedium`）、メタ行（`labelMedium` / `onSurfaceVariant`）= 役割の説明・大きさ・更新日時
- 未作成: メタ行に「未作成」（`expectedAbsent` なら `onSurfaceVariant`、そうでなければ `tertiary`）。押すと空の内容で開き、編集から始まる
- 押すと `AgentFileRoute(agentId, name)`

### スキル

- 一覧の頭に 1 行の補足（`bodyMedium` / `onSurfaceVariant`）: 有効・無効は Gateway 全体の設定であること
- 20 件を超えるときだけ、下部のピルの右に `VerticalDivider` で区切って検索のアイコンを出す（AGENTS.md 2.7「左に主操作、右に道具」）。押すとピルが検索欄に形を変え、名前と説明で絞り込む。戻るでタブに戻る（002 の `FileControls` の検索と同じ型）
- `ListItem`、key = skillKey。先頭 = 絵文字（無ければ空き）、タイトル = 名前、メタ行 = 状態と説明の 1 行目、末尾 = `Switch`
- 状態の色: 使える = `onSurfaceVariant`、要件が足りない = `tertiary`、無効 = `onSurfaceVariant` の 0.6
- 行を押すと共通のボトムシート: SKILL.md のプレビュー（不透明な面の中だけスクロール）。要件が足りないときは、その上に足りないものの一覧（コマンド・環境変数・設定・OS）
- スイッチ: 押した瞬間に見た目を変える。Snackbar（ガラスのピル）に「無効にしました / 有効にしました」と「元に戻す」

## AgentFileRoute(agentId, name) — 新しい画面

- 002 のファイルの画面と同じ骨格（共通の TopAppBar、`FileViewer`、下部の `FileControls`、衝突の `AlertDialog`、捨てる確認）
- タイトルはファイルの名前、サブタイトルなし（ホストのパスは出さない）
- 2 MiB を超えるとき: 編集のボタンを出さず、本文の上に理由を 1 行

## 状態の表示

- 取得中: 一覧の行の形のプレースホルダー（光を流す。見えているあいだだけ）
- 失敗: 理由と再試行のボタン（タブの中で）
- 手段が無い: 「この Gateway では使えません」（再試行なし）

## 文言（strings.xml、日本語だけ）

`agent_settings_*`・`agent_file_role_*`・`skill_state_*`・`skill_missing_*` の接頭辞で足す。アイコンだけのボタンは `*_cd`。
