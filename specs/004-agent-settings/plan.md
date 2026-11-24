# Implementation Plan: エージェントの設定（概要・ファイル・スキル）

**Branch**: `004-agent-settings`（ブランチは切らず main で進める） | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-agent-settings/spec.md`

## Summary

サイドパネルのエージェントの名前の横に編集のアイコンを置き、会話のアバターからも開ける「エージェントの設定」の画面を作る。
下部のピル型のタブで 概要 / ファイル / スキル を切り替える。ファイルは Gateway の `agents.files.*`（ハッシュと
`expectedMissing` で衝突を検出）で読み書きし、002 の「開いたファイル」の状態の移り変わりを `OpenedFileController` に切り出して
使い回す。スキルは `skills.status` で一覧と足りない要件を、`skills.files.get` で SKILL.md を読み、`skills.update` で有効・無効を
切り替える（先に見せて、失敗したら戻す）。調査は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001・002 と同じ）

**Primary Dependencies**: 既存のみ（Compose・M3・Navigation 3・Haze・OkHttp・Room・DataStore・highlights・Markdown の描画）。新しい依存なし

**Storage**: 既存の Room `file_drafts` に鍵 `agent:<agentId>:<name>` で入れる（テーブルの形・DB の版は変えない）。タブとスクロール位置は `rememberSaveable`

**Testing**: JUnit 4 ＋ 既存の偽の Gateway（`app/src/test/.../testing`）。Repository の往復（agents.files・skills）、`OpenedFileController` の状態の移り変わり、
スキルの状態の決め方と並び順、切り替えの先出しと戻し。002 の `FilesViewModelTest` を切り出しの回帰に使う

**Target Platform**: Android 13（API 33）以上

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: タブの切り替えとスイッチは押した瞬間に反映（SC-006）。サイドパネルから AGENTS.md の保存まで 30 秒以内（SC-002）

**Constraints**: Markdown の解析・色分けは ViewModel で一度だけ（AGENTS.md 1）。ホストのパス（`workspace`・`filePath`・`baseDir`）を画面に出さない。
秘密の値（`apiKey`・`env`）を送らない・表示しない

**Scale/Scope**: 画面 2 つ（設定・エージェントのファイル）＋シート 1 つ（スキルの詳細）。入口 2 か所。ストーリー 3 本、要件 FR-001〜042

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさが最優先 | ✅ | タブは選んだものだけを描いてフェード（R9）。スイッチは先に見せる（R5）。画面遷移は既存の右から重なる型 |
| II. デザインは AGENTS.md に従う | ✅ | 一覧は `ListItem`、詳細はボトムシート、ファイルは新しい画面（2.7 の「中身の多い詳細」）。ガラスはタイトルバー・下部のピル・シート・Snackbar だけ。アイコンは既存の Hugeicons ＋スキル用に 1 つ |
| III. Gateway が唯一の正 | ✅ | 衝突は Gateway のハッシュと原子的な作成に任せる（R1）。スキルの状態は切り替えのあと読み直す。呼び出しは contracts で確かめたものだけ |
| IV. 安全な接続と秘密情報 | ✅ | ホストのパスと秘密の値を出さない。`config.get` は使わない。権限は Gateway に従い、拒まれたら理由を示す |
| V. 状態を失わない | ✅ | 下書きは既存の `file_drafts`、タブと位置は `rememberSaveable`（R10） |
| VI. 簡潔さ | ✅ | 新しい依存なし。002 のファイルの状態を複製せず切り出す（R2） |
| VII. 持ち歩くための道具 | ✅ | 扱うのは指示のファイルの小さな修正とスキルの確認・止める／戻すだけ。作成・削除・導入・ツールの許可などは範囲外（spec Assumptions） |

**Phase 1 後の再確認**: data-model・contracts で新たな違反なし。

## Project Structure

### Documentation (this feature)

```text
specs/004-agent-settings/
├── spec.md
├── plan.md              # このファイル
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── gateway-rpc.md
│   └── ui.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks で作る
```

### Source Code (repository root)

```text
app/src/main/kotlin/jp/miyavi/android/clawdroid/
├── core/data/
│   ├── Models.kt                    # Agent に createdVia・creatorAgentId・createdAt
│   ├── SessionsRepository.kt        # agents.list の解析に上の 3 項目
│   ├── FileModels.kt                # FileRoot.AgentFile、OpenedFile に workspace・creating、editable の条件
│   ├── FilesRepository.kt           # agents.files.list / get / set（AgentFileEntry、agent_file_conflict）
│   └── Skills.kt                    # 新規: SkillStatus・SkillsRepository（skills.status / files.get / update）
├── feature/files/
│   ├── OpenedFileController.kt      # 新規: FilesViewModel から開いたファイルの状態を切り出す
│   ├── FilesViewModel.kt            # OpenedFileController に任せる（振る舞いは変えない）
│   └── FilesScreen.kt               # FilePage を FilesUiState ではなく OpenedState を受ける形にし、internal にして使い回す
├── feature/agent/                   # 新規
│   ├── AgentSettingsViewModel.kt    # 概要・ファイルの一覧・スキル・切り替えの先出し
│   ├── AgentSettingsScreen.kt       # ViewModel をつなぐ部分 ＋ AgentSettingsContent（タブ）
│   ├── AgentOverview.kt             # 概要のタブ
│   ├── AgentFileList.kt             # ファイルのタブ（役割の説明・未作成）
│   ├── AgentSkills.kt               # スキルのタブ・詳細のシート
│   ├── AgentFileViewModel.kt        # 1 ファイル（OpenedFileController を持つ）
│   └── AgentFileScreen.kt           # 002 の FilePage・FileControls・衝突のダイアログで描く
├── feature/chat/sidebar/SidebarContent.kt   # 名前の横の編集のアイコン、onOpenAgentSettings
├── feature/chat/message/Messages.kt          # アバターを押せるように
├── feature/chat/ChatScreen.kt                # 上の 2 つをナビゲーションにつなぐ
└── navigation/AppNavigation.kt               # AgentSettingsRoute(agentId)・AgentFileRoute(agentId, name)

app/src/main/res/
├── drawable/ic_huge_skill.xml       # 新規（Hugeicons Stroke Rounded の puzzle 系。スキルのタブ）
└── values/strings.xml               # agent_settings_*・agent_file_role_*・skill_*（日本語だけ）

app/src/test/kotlin/jp/miyavi/android/clawdroid/
├── core/data/FilesRepositoryTest.kt        # agents.files の往復・衝突・未作成の作成
├── core/data/SkillsTest.kt                 # 新規: 解析・状態・並び順・update
├── feature/files/OpenedFileControllerTest.kt   # 新規
└── feature/agent/AgentSettingsViewModelTest.kt # 新規: 先出しと失敗時の戻し、手段が無いとき
```

**Structure Decision**: 単一の `app` モジュール。新しい画面は `feature/agent/` にまとめ、データ層は `core/data` の Repository を通す
（画面から `GatewayClient` を呼ばない）。ファイルの読み書きは 002 の `FileSource` を広げて使う。

## 実装の順序（tasks の骨子）

1. **土台**: `OpenedFileController` の切り出し（002 のテストが通ること）→ `FileRoot.AgentFile` と `agents.files.*` → `Agent` の項目 → 文言・アイコン
2. **US1 ファイル**: `AgentFileRoute`（読む・編集・保存・衝突・作成・下書き）→ 設定の画面の骨格とファイルのタブ → 入口 2 か所
3. **US2 概要**: 概要のタブ（数・IDENTITY.md への導線・ワークスペースを開く）
4. **US3 スキル**: `SkillsRepository` → 一覧・状態・シート → 切り替え（先出し・戻し・元に戻す）→ 絞り込み
5. **仕上げ**: 手段が無い Gateway・権限の拒否・文字 200%・AGENTS.md 第 4 章・quickstart

## リスクと先に確かめること

- **002 のファイルビューアを壊すおそれ**: 004 は 002 の保存・衝突・下書きの仕組みを使い回すため、002 の中身を少し組み替える。組み替えた直後に 002 のテストと実機（保存・衝突・下書きを 1 回ずつ）で確かめ、壊れたら組み替えをやめて 002 の中にエージェントのファイルを足す形に切り替える
- **スキルの情報の形が Gateway の版で違うおそれ**: 形が決まっていないため、最初にマスターの Gateway の実際の応答を見てから作る。分からない項目は無視する
- **スキルの切り替えを Gateway が拒むおそれ**: 拒まれたら元に戻して理由を出す（仕様どおり）

## Complexity Tracking

> 憲章の違反なし。
