# Research: ターミナルとファイルビューア

**Date**: 2026-11-23 | **Spec**: [spec.md](./spec.md)

確かめた Gateway の版: OpenClaw 2026.11.6（`~/.nvm/.../openclaw/docs` と `dist` のスキーマ）。
001 の research R9（Termux terminal-view）と R10（highlights）は承認済みのまま引き継ぐ。

## R1. ファイルの一覧と読み取り（作業フォルダ）

- **Decision**: `sessions.files.list { sessionKey, agentId, path }` の `browser`（`path` / `parentPath` / `entries[{ path, name, kind: file|directory, size, updatedAtMs }]` / `truncated`）で辿る。開くのは `sessions.files.get { sessionKey, agentId, path }` の `file`（`content` / `contentEncoding: utf8|base64` / `previewKind: text|image|unsupported` / `hash` / `size` / `mimeType`）と `source`
- **Rationale**: Control UI の Files と同じ入口。`previewKind` で「文字 / 画像 / 表示できない」を Gateway が判定してくれる。`hash` が付けば作業フォルダの中で、編集できる
- **注意**: プレビューの上限は 256 KiB（`WORKSPACE_PREVIEW_MAX_BYTES`）。超えると内容は来ない → 読むだけ・ダウンロードのみ（FR-007）。作業フォルダの外のファイルは `hash` が無く、読むだけ（FR-012）
- **Alternatives considered**: `agents.files.*`（ブートストラップのファイルだけで、作業フォルダではない）

## R2. エージェントのワークスペース

- **Decision**: `agents.workspace.list { agentId, path, offset, limit }`（`entries` / `totalEntries` / `offset` / `parentPath`、ページ送り）と `agents.workspace.get { agentId, path }`（`file.encoding: utf8|base64`、`content`）。書き込みの RPC は無いので読むだけ（FR-002・012）
- **Rationale**: 公式資料のとおり `operator.read` で使える読み取り専用の窓口

## R3. 保存と衝突

- **Decision**: `sessions.files.set { sessionKey, agentId, path, content, expectedHash, expectedSource }`。`expectedHash` は開いたときの `hash`、`expectedSource` は開いたときの `source`。食い違うと Gateway が拒む → 衝突のダイアログ（上書き = 最新を取り直してその `hash` で再保存 / 読み直し = 下書きを捨てて取り直す）（FR-009）
- **Rationale**: Gateway 側の compare-and-swap に任せ、アプリでは比べない（憲章 III）
- **保存の上限**: 2 MiB の UTF-8（資料）。表示できるのが 256 KiB までなので、実際の上限は表示側で決まる

## R4. ダウンロード

- **Decision**: 作業フォルダは `sessions.files.download { sessionKey, agentId, path }` → `{ filename, mimeType, url, expiresAt }`。`url` は Gateway からの相対の短命リンク（5 分・この接続だけ）。接続先の `ws(s)://host` から `http(s)://host` を作り、OkHttp で GET して `MediaStore.Downloads` に書く（同じ名前は MediaStore が別名にする、FR-014）。ワークスペースは `agents.workspace.get` の内容をそのまま書く（256 KiB を超えるものは理由を出す、FR-015）
- **Rationale**: 大きいファイルも WebSocket に載せずに取れる。Downloads への保存は API 29 以上で許可が要らない
- **失敗時**: HTTP に届かない（`wss` だけ通す構成）なら、理由を示す（`session_file_download_unavailable` も同じ扱い）
- **Alternatives considered**: `artifacts.download` の base64（会話の成果物向けで、作業フォルダのファイルには使えない）、SAF で保存先を選ばせる（1 手増える）

## R5. ターミナル（接続だけのもの）

- **Decision**: `terminal.open { agentId, cols, rows }`（`sessionKey` を渡さない＝この接続だけのもの。spec FR-028 / マスター決定 B）→ `{ sessionId, shell, cwd, confined }`。入力 `terminal.input { sessionId, data }`、大きさ `terminal.resize { sessionId, cols, rows }`、出力はイベント `terminal.data { sessionId, seq, data }`、終わりは `terminal.exit { sessionId, exitCode, signal, reason: process_exit|closed|disconnected|detached|error }`
- **作業フォルダで始める（FR-021）**: `sessionKey` を渡さないと始まりはエージェントのワークスペースになる。開いた直後に `cd -- '<会話の作業フォルダ>' && clear` を `terminal.input` で送る（作業フォルダが無い・ワークスペースと同じなら送らない）。パスはシングルクォートで包み、中の `'` は `'\''` にする
- **つなぎ直し（FR-024）**: 端末内に `sessionId` を会話ごとに保存する。戻ったら `terminal.list` に残っていれば `terminal.attach { sessionId }` → `{ buffer, seq }` を表示し直してから続きを受ける。無ければ「消えた」と示し開き直しを出す。Gateway の保持は切断から既定 300 秒
- **別の場所で開かれた（FR-025）**: `terminal.exit` の `reason: detached` は、ほかの接続が `attach` で取ったとき。取り戻す = もう一度 `attach`
- **権限（FR-026）**: どの `terminal.*` も `operator.admin` が要る。接続の `scopes` に無い、または `features.methods` に `terminal.open` が無い（`gateway.terminal.enabled=false`）なら開かずに理由を出す
- **重複の除去**: `seq` が手元の最後以下のデータは捨てる（attach の buffer とイベントの重なり）

## R6. ターミナルの表示（001 R9 の具体化）

- **Decision**: Termux の `terminal-view`（`TerminalView` を `AndroidView` で）と `terminal-emulator` の `TerminalEmulator`。PTY は Gateway 側にあるので、`TerminalSession`（端末内のプロセス）は使わず、受け取ったバイト列を `TerminalEmulator.append` に渡し、キー入力は `TerminalViewClient` で受けて `terminal.input` に送る
- **フォントと色**: 等幅のフォント（`codeTextStyle` と同じ）、文字の大きさは端末の設定 × 固定の倍率。色はテーマの `surface` / `onSurface` を端末の既定の背景・文字色に当てる（ANSI の 16 色は Termux の既定のまま。ターミナルの中身は「内容」であり、テーマの色の規則の対象外）
- **特殊キーの行（FR-022）**: Compose のガラスのピル（下部に浮く。AGENTS.md 2.7 の主要な操作の形）に Esc・Tab・Ctrl（次の 1 文字と組み合わせる切り替え）・↑↓←→。押したキーは対応するエスケープ列で送る
- **コピー（FR-027）**: `TerminalView` の選択の仕組み（長押しで選択、メニューからコピー）

## R7. コードの表示と編集（001 R10 の具体化）

- **Decision**: 読むときは `highlights` の結果を `AnnotatedString` にして `Text`（等幅、`SelectionContainer`）。編集は `BasicTextField`（等幅）に切り替え、編集中は色分けしない（入力のたびの解析を避ける。AGENTS.md 1）
- **色**: 字句の種類ごとの色は `colorScheme` から当てる（キーワード = `primary`、文字列 = `tertiary`、コメント = `onSurfaceVariant`、数 = `secondary`）。直書きしない
- **大きさ**: 256 KiB を超えると内容が来ないので、色分けの重さはこの上限で収まる。解析は ViewModel（`Dispatchers.Default`）で一度だけ

## R8. 保存していない変更の保持（FR-011）

- **Decision**: Room に `file_drafts`（key = `root:sessionKey|agentId:path`、内容、開いたときの `hash`、更新時刻）を足す（DB の版 1 → 2、テーブルの追加だけの移行）。入力は 400ms 間引いて保存（会話の下書きと同じ）。保存できたら消す
- **開いたとき**: 下書きがあれば下書きを出し、「保存していない変更があります」を示す。下書きの `hash` と今の `hash` が違えば、保存時に衝突として扱う

## R9. 画面の構成

- **Decision**: 既存の `FilesRoute(sessionKey, agentId)` と `TerminalRoute(sessionKey)` を本物の画面にする。ファイルは 1 画面の中で「一覧 ⇄ 開いたファイル」を状態で切り替え、フォルダの移動は画面の積み重ねではなく一覧の中身の入れ替え（`BackHandler` で 1 段戻る。予測型「戻る」はフォルダが根のときだけ画面ごと）。画像は既存の全画面オーバーレイ（`ImageOverlay`）
- **Rationale**: 階層ごとに画面を積むと、深いフォルダで戻る動きが重なる。動きは一覧の入れ替え（新しい側だけフェード、AGENTS.md 2.8）
- **大画面**: list-detail（一覧と開いたファイルを左右）。Material 3 Adaptive に任せる
