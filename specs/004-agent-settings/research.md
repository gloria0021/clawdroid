# Research: エージェントの設定（概要・ファイル・スキル）

**Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

根拠は、インストール済みの Gateway の資料（`docs/gateway/protocol/rpc-talk-config-and-agents.md`・`operator-methods.md`・`concepts/agent-workspace.md`）、
プロトコルのスキーマ（`reference/openclaw/packages/gateway-protocol/src/schema/agents-files.ts`・`agents-models-skills.ts`）、
実装（`dist/status-*.mjs`・`dist/entry-status-*.mjs`）。メソッド名は `dist` で実在を確かめた。

## R1. エージェントのファイルの読み書き

- **Decision**: `agents.files.list` / `agents.files.get` / `agents.files.set` を使う。保存は、既存のファイルなら `expectedHash`、
  未作成のファイルなら `expectedMissing: true` を付け、どちらも読んだときの `workspace` を `expectedWorkspace` に入れる。
  衝突は `INVALID_REQUEST` の `details.type == "agent_file_conflict"` で判定する
- **Rationale**: Gateway がハッシュの比較と原子的な新規作成を受け持つので、アプリは比べない（憲章 III）。
  `expectedWorkspace` を付けると、編集中にエージェントのワークスペースが付け替えられた場合も衝突として止まる
- **Alternatives considered**: `agents.workspace.*`（書き込みが無い）、条件なしの上書き（spec FR-022 に反する）

## R2. ファイルビューアの使い回し方

- **Decision**: 002 の「開いたファイル」の状態の移り変わり（読む・プレビュー・検索・編集・保存・衝突・下書き）を
  `FilesViewModel` から `OpenedFileController`（ViewModel の中で使う普通のクラス）に切り出し、002 の画面と本機能の
  ファイルの画面の両方で使う。データの窓口 `FileSource` に新しいルート `FileRoot.AgentFile(agentId)` を足し、
  `FilesRepository` が `agents.files.*` を受け持つ。画面の部品（`FileViewer`・`FileControls`・衝突のダイアログ）はそのまま使う
- **Rationale**: spec FR-021 は「002 と同じ体験・同じ部品」。複製すると衝突や下書きの直しが 2 か所になる（憲章 VI）。
  `FilesViewModel` は一覧・フォルダの移動・ルートの切り替えを持っており、平らな一覧で 1 ファイルだけを開く本機能には合わない
- **Alternatives considered**: `FilesScreen` に 3 つ目のルートとして足す（フォルダの移動・作業フォルダとの切り替えの UI が混ざる）、
  本機能専用に書き直す（重複）

## R3. 大きなファイルと読み込みの上限

- **Decision**: `size` が 2 MiB（2,097,152 バイト）を超えるファイルは読むだけにする（`agents.files.set` の上限）。
  起動時に読み込まれる上限（1 ファイル約 2 万字）の警告は出さない（spec FR-025 は削除）
- **Rationale**: 本家の Control UI のファイルの編集（`agents-page`、文言 `agents.files.*`）は、語数・行数・未作成・衝突は示すが、
  読み込みの上限の警告は出していない。マスター指定で本家に合わせる
- **Alternatives considered**: `config.get` で上限の値を取って警告する（設定の全体を取ることになり過剰。本家もしていない）

## R4. スキルの一覧と状態

- **Decision**: `skills.status({ agentId })` の `skills[]` を使う。各項目の `name`・`description`・`emoji`・`skillKey`・`filePath`・
  `source`・`bundled`・`eligible`・`disabled`・`blockedByAllowlist`・`blockedByAgentFilter`・`platformIncompatible`・
  `missing { bins, anyBins, env, config, os }` を読む。状態は次の順で決める
  - `disabled` → 無効
  - `eligible` → 使える
  - それ以外 → 要件が足りない（理由は `missing` の空でない項目と、`blockedByAllowlist`・`blockedByAgentFilter`・`platformIncompatible`）
- **Rationale**: スキーマは結果を型で定めていないため、実装（`buildWorkspaceSkillStatus`）の出力に合わせる。知らない項目は無視し、
  欠けている項目は「無い」として扱う（Gateway の版の違いに耐える）
- **Alternatives considered**: `skills.bins`（コマンドの有無だけ）

## R5. スキルの有効・無効

- **Decision**: `skills.update({ skillKey, enabled })`（設定のモード）。見た目は先に変え、失敗したら戻す。成功後に
  `skills.status` を 1 回読み直して状態をそろえる。「元に戻す」は逆の値で同じ呼び出しをする
- **Rationale**: Gateway の `skills.entries.<skillKey>.enabled` を書き換える操作で、エージェントごとではない（spec FR-034 の注記の根拠）。
  `operator.admin` が要るが、アプリは接続時に求めている
- **Alternatives considered**: なし（ほかに手段が無い）

## R6. SKILL.md を読む

- **Decision**: `skills.files.get({ agentId, path: filePath })` の `content` を、002 の Markdown のプレビューで読むだけにする
- **Rationale**: 書き込みの手段は無い（spec Assumptions）。`path` は `skills.status` の `filePath` をそのまま渡せる
- **Alternatives considered**: `agents.workspace.get`（ワークスペースの外のスキルが読めない）

## R7. 概要の情報

- **Decision**: 名前・絵文字・顔・既定のモデルは既存の `agents.list` の結果（`Agent`）を使う。作られた経緯は `agents.list` の
  `createdVia`・`creatorAgentId`・`createdAt` を `Agent` に足して読む（無ければ行ごと出さない）。ファイルとスキルの数は、
  それぞれのタブのデータを読んだ結果から数える
- **Rationale**: 新しい呼び出しを増やさない。`agent.identity.get` は `agents.list` と同じ名前・絵文字を返すだけで、足すものが無い
- **Alternatives considered**: `agent.identity.get` を別に呼ぶ（重複）

## R8. 入口

- **Decision**: サイドパネルの看板のエージェントの名前を `Row` にし、右に `IconButton`（`ic_huge_edit`、48dp）を置く。
  会話の応答の横のアバターに `clickable(onClickLabel = …)` を付ける。どちらも `AgentSettingsRoute(agentId)` を開く
- **Rationale**: spec FR-001・002。アイコンは既にある `ic_huge_edit.xml` を使い、新しいアイコンは要らない
- **Alternatives considered**: 看板の画像を押すと開く（押せることが分からない。マスターの判断どおり明示のアイコンにする）

## R9. 画面の構成

- **Decision**: `AgentSettingsRoute(agentId)` は 1 画面。下部に浮くガラスの `PillTabs`（既存）で「概要」「ファイル」「スキル」を切り替える。
  タブの中身は `HorizontalPager` を使わず、選んだタブだけを描き、新しい側をフェードで入れる（AGENTS.md 2.8「重いリストどうしで
  `AnimatedContent` を使わない」）。ファイルを押すと `AgentFileRoute(agentId, name)` が右から重なる。スキルの詳細（足りない要件・SKILL.md）は
  共通のボトムシート
- **Rationale**: spec FR-003・004。ファイルを読む・直すのは中身が多いので新しい画面、スキルの詳細は短い補助表示なのでシート（AGENTS.md 2.7）
- **Alternatives considered**: タブを上部の `PrimaryTabRow` にする（AGENTS.md 2.7 で下部の切り替えはピル型のタブと決まっている）

## R10. 状態の保持

- **Decision**: 選んだタブと各タブのスクロール位置は `rememberSaveable`（`LazyListState.Saver`）。ファイルの下書きは既存の
  `file_drafts` に `agent:<agentId>:<name>` の鍵で入れる（テーブルの形は変えない。DB の版はそのまま）
- **Rationale**: spec FR-004・SC-004、憲章 V
- **Alternatives considered**: DataStore にタブを保存する（画面の外で使わない値なので過剰）

## R11. 概要の中身（2026-11-24 追加、R3・R7 の一部を改める）

- **Decision**: `config.get` の `config.agents.entries.<agentId>` を読み、無い項目は `agents.defaults` と `tools`（Gateway 全体）から補って「既定」の印を付ける。読むのはモデル・思考の既定・`tools.profile / allow / alsoAllow / deny / exec.mode / elevated.enabled / fs.workspaceOnly`・`tools.web.search`・ワークスペースの名前（パスの最後だけ）
- **Rationale**: マスターの指定で、概要は openclaw.json の定義を見せるものにした。R3 では「設定の全体には秘密が含まれる」として `config.get` を避けたが、資料（`gateway/configuration.md`）で `config.get` が秘密を伏せた写しを返すと確かめたので使う。マスターの Gateway の実際の応答で形を確かめた（エントリは id をキーにしたオブジェクト。古い配列の形にも対応）
- **Alternatives considered**: `tools.effective`（会話ごとの見込みで、エージェントの定義そのものではない。sessionKey が要る）、`tools.catalog`（実行時のツールの一覧で、許可・拒否の定義ではない）
