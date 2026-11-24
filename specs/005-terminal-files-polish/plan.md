# Implementation Plan: ターミナルとファイルの磨き込み

**Branch**: `005-terminal-files-polish`（ブランチは切らず main で進める） | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-terminal-files-polish/spec.md`

## Summary

ターミナルには、特殊キーの行の「^C」と「貼り付け」、外付けキーボードの Ctrl+Shift+C / V、新しく開いたときだけ手元で描く
OpenClaw の看板（マスコットとロゴ。桁数で 3 つの形から選ぶ）を足す。どれも既存の Termux の `TerminalView` と
`RemoteTerminalBridge` の上で完結し、Gateway への新しい要求は無い。

ファイルは、フォルダを 1 段ずつ移る `NavDisplay` のページをやめ、その場で開閉するツリー（平らにした行の `LazyColumn`）にする。
スマホではツリーを左から引き出すパネル（`ModalNavigationDrawer`）、2 ペインを置ける幅ではツリーを左に出したままにする
（判定は Material 3 Adaptive の `calculatePaneScaffoldDirective` の区画数）。コードは行番号・色分け・今の行の強調・折り返しの
切り替え・行へ移る・取り消し / やり直し・記号の行を持つエディターにする。読むときは行ごとの `LazyColumn`、編集は 1 つの
`BasicTextField` に色分けを載せる。

ファイルの作成・削除・名前の変更は Gateway のファイルの API に無いため、ターミナルの仕組み（`terminal.open` → `terminal.input`
→ `terminal.data` → `terminal.close`）で使い捨てのシェルを開き、作業フォルダの外へ出ないことをシェルの側でも確かめる短い
スクリプトを 1 回だけ流して結果を読む。調査は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001〜004 と同じ）

**Primary Dependencies**: 既存のみ（Compose BOM 2026.09.00・M3・M3 Adaptive 1.3.0・Navigation 3・Haze・OkHttp・Room・
termux terminal-view v0.118.3・highlights 1.2.0）。新しい依存なし

**Storage**: 開いていたフォルダ・開いていたファイル・ツリーのスクロール位置は `SavedStateHandle`（プロセスの終了を越える）。
折り返しの選択は既存の設定の DataStore。保存していない変更は既存の Room `file_drafts`（秘密の値を含むファイルは別扱い。research R8）

**Testing**: JUnit 4 ＋ 既存の偽の Gateway（`app/src/test/.../testing/FakeGateway.kt`）。看板の形の選び方、ツリーの平らにする処理と展開の保持、
行番号の対応、名前の検証、ファイル操作のスクリプトの組み立てと結果の読み取り（偽の `terminal.data` で往復）、取り消しの履歴

**Target Platform**: Android 13（API 33）以上

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: 「^C」は押した瞬間に送る（SC-002）。3,000 行のファイルでスクロール・入力がカクつかない（SC-008）。
見えている範囲の色分けは開いてから 1 秒以内（SC-009）

**Constraints**: 色分けは ViewModel / `remember(key)` で行い、行の Composable の中で解析しない（AGENTS.md 1）。
ファイル操作は作業フォルダの外に出ない（FR-064）。看板はシェルに送らない（FR-022）。Ctrl+C は割り込み、Ctrl+Shift+C はコピー（FR-002・010）

**Scale/Scope**: 画面は既存の 2 つ（ターミナル・ファイル）を作り直す。ストーリー 6 本、要件 FR-001〜068

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさ | 適合 | パネルの出し入れは M3 の `ModalNavigationDrawer` の標準の動き。色分けは裏で行い、終わる前でも素の文字で読める。看板は動かさない |
| II. AGENTS.md | 適合（要報告 1 件） | 色はトークン（行番号 `onSurfaceVariant`、今の行 `onSurface` 5%）。アイコンは Hugeicons（ツリーの開閉・ファイル・フォルダは既存、`^C`・貼り付け・行へ移る・取り消し / やり直し・折り返しは足す）。大画面の出し分けは list-detail ではなく M3 Adaptive の区画数の判定で行う（下の Complexity Tracking） |
| III. Gateway が唯一の正 | 適合 | ツリーは毎回 `sessions.files.list` から作る。作成・削除・名前の変更のあとは取り直して結果を確かめる（FR-067） |
| IV. 安全な接続と秘密情報 | 適合 | ファイル操作はターミナルと同じ `operator.admin` の範囲でだけ出す。名前はアプリとシェルの二重で検証し、作業フォルダの外へは及ばない。秘密の値を含む下書きはバックアップ・引き継ぎに入れない（R8） |
| V. 状態を失わない | 適合 | 展開・開いていたファイル・スクロール位置・折り返しを保持。削除・名前の変更の前に保存していない変更を確かめる |
| VI. 簡潔さ | 適合 | 新しい依存なし。エディターは標準の `BasicTextField` の上に行番号と色を載せるだけ |
| VII. 持ち歩く道具 | **逸脱（承認済み）** | 憲章は「作成と削除のような構造の操作は Web UI・CLI に任せる」とするが、US6 で入れる（下の Complexity Tracking） |

設計後の再確認: research と data-model で上の判定は変わらない。逸脱は 2 件とも Complexity Tracking に理由を書いた。

## Project Structure

### Documentation (this feature)

```text
specs/005-terminal-files-polish/
├── plan.md              # このファイル
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── file-ops-shell.md   # 使い捨てのシェルで流すスクリプトと結果の形
│   └── ui.md               # 画面の構成・キー・メニュー
└── tasks.md             # /speckit-tasks で作る
```

### Source Code (repository root)

```text
app/src/main/kotlin/jp/miyavi/android/clawdroid/
├── core/data/
│   ├── TerminalRepository.kt      # 使い捨てのシェルを開いて 1 回流す runOnce() を足す
│   └── FileOpsRepository.kt       # 新規: 作成・削除・名前の変更（スクリプトの組み立てと結果の読み取り）
├── core/database/ClawdroidDatabase.kt   # file_drafts に secret の印（R8）
├── feature/terminal/
│   ├── TerminalBanner.kt          # 新規: 看板の絵と、桁数での形の選択
│   ├── TerminalScreen.kt          # Ctrl+Shift+C/V、看板の描画
│   ├── TerminalViewModel.kt       # 割り込み・貼り付け・看板を出すか
│   └── SpecialKeysRow.kt          # ^C・貼り付け、横スクロール
└── feature/files/
    ├── FileTree.kt                # 新規: ツリーの行（平らにした一覧）とパネル
    ├── FileTreeState.kt           # 新規: 展開・読み込み・平らにする処理（純粋な関数）
    ├── CodeEditor.kt              # 新規: 行番号・色分け・今の行・折り返し・行へ移る
    ├── EditHistory.kt             # 新規: 取り消し / やり直し
    ├── SymbolRow.kt               # 新規: キーボードの上の記号の行
    ├── FileOpsDialogs.kt          # 新規: 作成・名前の変更・削除のダイアログ
    ├── FilesScreen.kt             # NavDisplay のページ → パネル / 2 ペイン
    ├── FilesViewModel.kt          # ツリーの状態・ファイル操作
    ├── FileViewer.kt              # CodeView・Editor を CodeEditor に置き換え
    └── FileList.kt                # 削除（ツリーに置き換え）

app/src/test/kotlin/jp/miyavi/android/clawdroid/
├── feature/terminal/TerminalBannerTest.kt
├── feature/files/FileTreeStateTest.kt
├── feature/files/EditHistoryTest.kt
├── feature/files/LineIndexTest.kt
└── core/data/FileOpsRepositoryTest.kt
```

**Structure Decision**: 既存の単一モジュールの縦の切り口（UI → ViewModel → Repository）に足す。ファイル操作は
ファイルの API とは別の Repository に分け、ターミナルの低い層（開く・入力・出力・閉じる）を `TerminalRepository` から借りる。

## Complexity Tracking

| 逸脱 | なぜ必要か | より単純な案を退けた理由 |
|---|---|---|
| 憲章 VII「作成と削除のような構造の操作は Web UI・CLI に任せる」に反して、ファイルの作成・削除・名前の変更を入れる | マスターの指定（2026-11-24）。外出先でファイルを 1 つ置く（設定ファイル・鍵のファイルなど）ことがよくあり、エージェントに頼むと秘密の値が会話の記録に残る | 「エージェントに頼む」は記録に残る。「Web UI で行う」は外出先で使えない。移動・アップロード・複数選択は入れず、1 件ずつの作成・名前の変更・削除に絞る |
| 大画面の出し分けを list-detail（`ListDetailSceneStrategy`）ではなく、M3 Adaptive の `calculatePaneScaffoldDirective(...).maxHorizontalPartitions` で行う | スマホではツリーを「引き出すパネル」にする指定（spec FR-041）で、list-detail の 1 ペイン時の振る舞い（別の画面として積む）と合わない | 画面幅の数値で自前に分けるのは AGENTS.md 1 で禁止。区画数は Adaptive が決める値なので、出し分けの判断は Adaptive に任せたまま |
| 作成・削除・名前の変更をシェルで行う | Gateway のファイルの API（2026.11.6）は既存のファイルの読み書きだけ（spec Assumptions） | Gateway への API の追加を待つ案はマスターが退けた（2026-11-24） |
