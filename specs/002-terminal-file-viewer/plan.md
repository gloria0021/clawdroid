# Implementation Plan: ターミナルとファイルビューア

**Branch**: `002-terminal-file-viewer`（ブランチは切らず main で進める） | **Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-terminal-file-viewer/spec.md`

## Summary

会話のタイトルバーにある「準備中」のファイル・ターミナルの画面を本物にする。ファイルは Gateway の
`sessions.files.*`（作業フォルダ、読み書き・ダウンロード）と `agents.workspace.*`（ワークスペース、読むだけ）で辿り、
コードは highlights で色分け、編集は `BasicTextField`、保存は Gateway のハッシュ比較に任せる。ダウンロードは
短命の HTTP リンクを OkHttp で取り、端末の Download に書く。ターミナルは `terminal.*` の接続だけの PTY を
Termux の `TerminalView` に映し、特殊キーの行をガラスのピルで足す。調査は [research.md](./research.md)。

## Technical Context

**Language/Version**: Kotlin 2.4.21、JVM 21（001 と同じ）

**Primary Dependencies**: 既存（Compose・M3・Navigation 3・Haze・OkHttp・Room・DataStore）に、承認済みの
Termux `terminal-view`（＋ `terminal-emulator`、jitpack v0.118.3）と `dev.snipme:highlights` 1.2.0 を `app` の依存に足す（版の定義と jitpack の登録は済み）

**Storage**: Room に `file_drafts` を足す（DB 版 1 → 2、テーブルの追加だけ）。DataStore に会話ごとのターミナルの `sessionId`

**Testing**: JUnit 4（Repository の往復は既存の偽の Gateway `FakeGateway`）。ViewModel の状態の移り変わり、パスのクォート、特殊キーのエスケープ列、並び順、衝突の判定を単体テスト

**Target Platform**: Android 13（API 33）以上。Downloads への保存は `MediaStore.Downloads`（許可は不要）

**Project Type**: mobile-app（単一の `app` モジュール）

**Performance Goals**: 一覧 500 項目でコマ落ちなし（SC-002）、ターミナルの開始 3 秒以内（SC-007）、入力の反映 0.2 秒以内（SC-009）

**Constraints**: 色分けの解析は ViewModel で一度だけ（AGENTS.md 1）。動きは motionScheme のトークンだけ。ガラスはタイトルバー・下部のピル・シート・Snackbar だけ（AGENTS.md 2.10）

**Scale/Scope**: 画面 2 つ（ファイル・ターミナル）＋ダイアログ 2 つ（衝突・捨てる確認）。ストーリー 4 本、要件 FR-001〜031

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. 動きの心地よさが最優先 | ✅ | フォルダの移動は一覧の入れ替え（新しい側だけフェード）。重い色分けは ViewModel で先に（R7）。ターミナルの出力は `TerminalView` の描画に任せ、Compose の再構成を起こさない |
| II. デザインは AGENTS.md に従う | ✅ | 一覧は `ListItem`、確認は `AlertDialog`、通知は Snackbar、特殊キーはガラスのピル。ターミナルの中の ANSI の色は「内容」として扱う（R6）。外部の部品 2 つは 001 で承認済み |
| III. Gateway が唯一の正 | ✅ | 衝突の判定は Gateway の `expectedHash`（R3）。RPC はスキーマで確かめたものだけ（contracts） |
| IV. 安全な接続と秘密情報 | ✅ | ターミナルは接続だけのもの（エージェントから見えない、FR-028）。権限は Gateway に従い、無ければ開かない。ダウンロードのリンクは保存しない |
| V. 状態を失わない | ✅ | 保存していない変更を Room に（R8）、ターミナルの `sessionId` を DataStore に（R5） |
| VI. 簡潔さ | ✅ | 新しい依存は承認済みの 2 つだけ。画面はそれぞれ ViewModel ＋ `*Content` |

**Phase 1 後の再確認**: data-model・contracts で新たな違反なし。

## Project Structure

### Documentation (this feature)

```text
specs/002-terminal-file-viewer/
├── spec.md
├── plan.md              # このファイル
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/gateway-rpc.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks で作る
```

### Source Code (repository root)

```text
app/src/main/kotlin/jp/miyavi/android/clawdroid/
├── core/data/
│   ├── FilesRepository.kt          # sessions.files.* / agents.workspace.*（一覧・取得・保存・ダウンロードのリンク）
│   ├── FileDownloader.kt           # 短命リンクを OkHttp で取り、MediaStore.Downloads に書く
│   └── TerminalRepository.kt       # terminal.* とイベント（data / exit）、sessionId の保存
├── core/database/ClawdroidDatabase.kt   # file_drafts を追加（版 2）
├── feature/files/
│   ├── FilesViewModel.kt           # 一覧・開いたファイル・編集・保存・衝突・下書き
│   ├── FilesScreen.kt              # ViewModel をつなぐ部分 ＋ FilesContent
│   ├── FileList.kt                 # 一覧（ListItem、key 付き）とパスの表示
│   ├── FileViewer.kt               # 読む（色分け）・編集（BasicTextField）・表示できない
│   └── CodeHighlighter.kt          # highlights → AnnotatedString（colorScheme の色）
├── feature/terminal/
│   ├── TerminalViewModel.kt        # 開く・つなぎ直す・状態
│   ├── TerminalScreen.kt           # TerminalView を AndroidView で ＋ 状態の表示
│   ├── RemoteTerminalSession.kt    # Termux の TerminalSession を Gateway の PTY につなぐ
│   └── SpecialKeysRow.kt           # Esc・Tab・Ctrl・矢印（ガラスのピル）
└── navigation/AppNavigation.kt     # FilesRoute / TerminalRoute を本物の画面に

app/src/test/kotlin/jp/miyavi/android/clawdroid/
├── core/data/FilesRepositoryTest.kt
├── core/data/TerminalRepositoryTest.kt
└── feature/files/FilesViewModelTest.kt
```

**Structure Decision**: 001 と同じく単一の `app` モジュール。データ層は `core/data` の Repository を通し、画面から
`GatewayClient` を直接呼ばない。

## 実装の順序（tasks の骨子）

1. **土台**: 依存の追加（Termux・highlights）、`file_drafts`（DB 版 2）、Hugeicons の不足分、文言
2. **US1 読む**: FilesRepository（list / get / workspace）→ FilesViewModel → 一覧・パス・ルートの切り替え → 読む（色分け）・画像・表示できない
3. **US2 直す**: 編集・保存・衝突・捨てる確認・下書き
4. **US3 ダウンロード**: download → FileDownloader → Snackbar の「開く」
5. **US4 ターミナル**: RemoteTerminalSession の成立確認（R6 のリスク）→ TerminalRepository → 画面・特殊キー → つなぎ直し・状態の表示
6. 仕上げ: 大画面の list-detail、文字 200%、AGENTS.md 第 4 章、quickstart

## リスクと先に確かめること

| リスク | 確かめ方 | 外れたとき |
|---|---|---|
| Termux の `TerminalSession` が端末内のプロセスを前提にしている（Gateway の PTY をつなげない） | 段階 5 の最初に、`TerminalSession` を継ぐか `TerminalEmulator` を直接作って `TerminalView` に映せるかを試す | `TerminalView` を使わず、`TerminalEmulator` の画面を自前の Canvas で描く（相談してから） |
| Gateway の HTTP が端末から届かない（`wss` だけ通す構成） | 段階 4 で実機から GET | ダウンロードは理由を出す。256 KiB 以下は get の内容から保存する |
| 256 KiB を超えるファイルが多く、読めない場面が目立つ | 段階 2 で実際の作業フォルダを見る | 「大きすぎて表示できない」とダウンロードを出す（spec どおり） |

## Complexity Tracking

> 憲章の違反なし（外部の部品 2 つは 001 で承認済み）。
