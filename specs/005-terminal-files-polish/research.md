# Research: ターミナルとファイルの磨き込み

調べた元: インストール済みの OpenClaw 2026.11.6（`dist/`・`docs/`）、`reference/openclaw/packages/gateway-protocol/src/schema/terminal.ts`、
termux terminal-view v0.118.3 の `classes.jar`（`javap`）、このリポジトリの実装。

## R1. 割り込み（Ctrl+C）の送り方

- **Decision**: 「^C」は `\u0003`（ETX）を `terminal.input` で送る。既存の `TerminalViewModel.sendKey` に `SpecialKey.INTERRUPT` を足し、
  Ctrl の一時押し（`ctrlArmed`）は使わずに直接書く。外付けキーボードの Ctrl+C は `TerminalView.onKeyDown` が `KeyHandler` で
  ETX にするので、Shift が押されていないときはそのまま任せる。
- **Rationale**: Gateway の PTY は端末の行の規律（ISIG）で ETX を SIGINT に変える。シグナルを直接送る RPC は無い（terminal.* は open / input /
  resize / close / attach / list / upload だけ）。
- **Alternatives**: Esc を割り込みにする → ESC は文字であり、シェルも普通のコマンドも止まらない（spec Clarifications）。
  `terminal.close` で殺す → シェルごと失い、002 の「続きから使える」を壊す。

## R2. Ctrl+Shift+C / V

- **Decision**: `createTerminalView` の `TerminalViewClient.onKeyDown(keyCode, e, session)` で、`e.isCtrlPressed && e.isShiftPressed` の
  `KEYCODE_C` / `KEYCODE_V` を先に取り、`true` を返して既定の処理（Ctrl+C の ETX）を止める。
  - C: `TerminalView.getSelectedText()`（選択中）→ 無ければ `getStoredSelectedText()`。どちらも空なら何もしない。入れたら
    `stopTextSelectionMode()` で選択を解き、既存の `onCopy`（Snackbar）を呼ぶ。
  - V: 既存の `onPasteTextFromClipboard` と同じ道 `bridge.emulator.paste(text)` を使う。`TerminalEmulator.paste` は bracketed paste
    （`ESC[200~ … ESC[201~`）が有効なら囲み、制御文字を落とす（FR-013 とエッジケースの「制御文字」を満たす）。
  - 特殊キーの行の「貼り付け」も同じ関数を呼ぶ。クリップボードが空・文字でなければ何もしない。
- **Rationale**: v0.118.3 の `TerminalView` は `isSelectingText()`・`getSelectedText()`・`getStoredSelectedText()`・`stopTextSelectionMode()` を公開している（javap で確認）。
  Ctrl+Shift の割り当ては Termux 本体ではアプリ側（`TermuxTerminalViewClient`）の役目で、ライブラリは持たない。
- **Alternatives**: Ctrl+C をコピーにする（選択中だけ）→ 選択の有無で意味が変わり、止めたいときにコピーになる事故がある。PC の端末の慣例どおり Shift 付きに分ける。

## R3. 看板（OpenClaw のアスキーアート）

- **Decision**: 本家 `dist/claw-banner-*.mjs` の `MASCOT_ART`（20 桁 × 10 行）と `WORDMARK_ART`（47 桁 × 3 行、マスコットの 4 行目から右へ 3 桁空けて並ぶ。合計 71 桁）を
  そのまま写す。桁数で形を選ぶ純粋な関数 `bannerLines(columns)`:
  - 71 桁以上 → 横に並べる（本家と同じ）
  - 47 桁以上 → マスコットの下にロゴ（中央寄せ）
  - 20 桁以上 → マスコットの下に「OpenClaw」の文字
  - それ未満 → 「OpenClaw」の 1 行だけ
  マスコットは ANSI の SGR で強調色、ロゴは既定の文字色で、`RemoteTerminalBridge.append()` に手元で流す（シェルに送らない）。
  強調色は端末の配色の中の 1 色を使う（R3-1）。
- **出す時点**: `TerminalViewModel.start()` で新しく `terminal.open` したときだけ、最初の出力より前。`reattach`（つなぎ直し・取り戻し）では出さない。
  桁数は `onSize` で分かってから組み立てる（分かる前に開かない今の流れのまま）。
- **R3-1 色**: マスコットは SGR 38;2（24 ビット色）でテーマの `primary` の RGB を直接指定し、描き終えたら SGR 0 で戻す。
  ANSI の 16 色の配色は変えない（シェルの出力の色を変えない。002 FR-029）。Termux のエミュレータは 24 ビット色に対応している。
  テーマを変えても描いた看板の色は変わらない（出力として流れたもののため）。
- **Rationale**: 本家と同じ絵で「OpenClaw の道具」と分かる。手元で描くのでシェルの履歴・Gateway の記録に入らない（FR-022）。
- **Alternatives**: シェルに `printf` で描かせる → 履歴に残り、桁数の判断がシェル側にずれる。本家の動く看板 → 開いたときの演出は入れない（AGENTS.md 2.8）。
- **ライセンス**: OpenClaw は MIT。`assets/licenses/` に OpenClaw の MIT 表示を足す。

## R4. ツリーの作り

- **Decision**: 状態は `FileTreeState(expanded: Set<String>, children: Map<String, Listing>, selected: String?)`。`Listing` は
  読み込み中 / 項目 / 失敗。表示は純粋な関数 `flatten(root, state): List<TreeRow>` で、展開したフォルダの子を深さ付きで並べた
  平らな一覧にし、`LazyColumn` に `key = path` で渡す（AGENTS.md 1）。フォルダを開いたときに初めて `sessions.files.list(path)` を呼ぶ。
  `expanded`・`selected`・スクロール位置は `SavedStateHandle` に入れ、`children` はキャッシュ（取り直せる）。
- **字下げ**: 1 段 `Spacing.l`。8 段から先は増やさない（エッジケース「深い階層」）。
- **多い項目**: `sessions.files.list` が返すページを続けて読む既存の仕組みを、フォルダごとに使う。
- **Rationale**: 平らな一覧なら 1 つの `LazyColumn` で済み、展開・折りたたみは `animateItem` で動く。木の Composable を入れ子にすると
  遅延描画が効かない。
- **Alternatives**: 既存の `NavDisplay` のページ（フォルダごとの画面）→ ツリーの要件（その場で開閉）を満たさない。

## R5. パネルと 2 ペインの出し分け

- **Decision**: `calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).maxHorizontalPartitions` が 2 以上なら左にツリー
  （幅は Adaptive の既定のリストの幅）・右にコードを並べる。1 なら `ModalNavigationDrawer` でツリーを引き出すパネルにする。
  パネルの中身・見た目は会話のサイドパネルにそろえる（`ModalDrawerSheet` の色と形は会話の画面と同じ）。
  タイトルバーにツリーを開くボタン（Hugeicons の folder-tree 系。無ければ追加）を置く。ファイルを押したらパネルを閉じる。
  ファイルを開いていないときはパネルを開いた状態で始める。
- **横スクロールとのぶつかり**: コードの横スクロールは子が先にドラッグを使うので、左端までスクロールしきっていない限りパネルは開かない
  （`ModalNavigationDrawer` はネストしたスクロールの残りだけで動く）。実機で確かめる（quickstart）。
- **Rationale**: 判定を Adaptive に任せ、画面幅の数値を書かない（AGENTS.md 1）。
- **Alternatives**: list-detail の `ListDetailSceneStrategy` → 1 ペインでは別の画面として積むため、パネルの指定と合わない。

## R6. コードエディター

- **読む**: 行ごとの `LazyColumn`（`key = 行番号`）。行番号の列は等幅・`onSurfaceVariant`・右寄せで、幅は最大の行番号の桁数で決める。
  色分けは既存の `CodeHighlighter`（highlights）で全文の字句の範囲を ViewModel で一度作り、行ごとに切り出した `AnnotatedString` を
  `remember(行番号, 版)` で持つ。折り返さないときは全体を 1 つの横スクロールに載せる（行ごとに横スクロールさせない）。
  これで 3,000 行でも見えている行だけを組み立てる（SC-008）。
- **編集**: 1 つの `BasicTextField(TextFieldState)`。色分けは `OutputTransformation` の中で `TextFieldBuffer.addStyle`
  （Compose foundation 1.10 以降）で、直近の字句の範囲を当てる。字句の範囲は入力が止まって 150ms 後に裏で作り直し、
  それまでは古い範囲を編集の位置でずらして使う。**要確認**: BOM 2026.09.00 の foundation で `addStyle` が使えるか。
  使えなければ値の `BasicTextField(TextFieldValue)` ＋ `VisualTransformation`（色だけを付ける。位置の対応は恒等）に落とす。
- **行番号（編集中）**: `onTextLayout` の `TextLayoutResult` で、各論理行の先頭の `getLineTop` を取り、左の列に `drawBehind` で描く。
  折り返したときは論理行の先頭にだけ番号を付ける（spec US5 シナリオ 3）。
- **今の行**: 選択の位置から論理行を求め、その行の上下（`getLineTop`/`getLineBottom`）に `onSurface` 5% の面を `drawBehind` で敷く
  （本文の下地の色と同じ考え方。AGENTS.md 2.2）。
- **行へ移る**: 下部のピルの道具に「行へ移る」を足し、番号を入れる小さな `AlertDialog`。読むときは `scrollToItem`、編集中は選択を
  その行の先頭に動かして `bringIntoView`。
- **取り消し / やり直し**: `TextFieldState.undoState`（foundation 標準）を使う。値の方式に落とした場合は `EditHistory`（最大 200 件、
  連続の入力は 1 秒でまとめる）を自作する。
- **記号の行**: 編集中だけ、キーボードの上（`WindowInsets.ime`）にガラスのピルで `Tab ( ) { } [ ] < > " ' ` ; : = / \ | - _` を横スクロールで並べる。
  Tab は 2 か 4 の空白ではなく、ファイルの既存のインデント（先頭の空白の最頻値）に合わせる。無ければ 4 の空白。
- **大きいファイル**: 編集の色分けは 5,000 行まで。それより多いと編集中だけ色分けを止め、行番号は残す（SC-008 を優先。憲章 I）。
- **Rationale**: 読むことが多いので、読むときは遅延描画の一覧で最も軽くする。編集は標準の部品の上に足すだけ（憲章 VI）。
- **Alternatives**: 外部のエディター部品（Sora Editor など）→ 依存が重く、M3 のトークン・ガラスの規則に合わせにくい。行ごとの `TextField` の一覧で編集 → 行をまたぐ選択・貼り付けが壊れる。

## R7. 作成・削除・名前の変更（使い捨てのシェル）

- **Decision**: `FileOpsRepository` が `TerminalRepository` の低い層を使い、1 操作ごとに:
  1. `terminal.open`（`agentId`、`sessionKey` は付けない。エージェントと共有しない）。`cols = 200, rows = 24`
  2. `terminal.input` で 1 行のコマンドを送る: `stty -echo; sh -c '<スクリプト>' sumahoclaw-op <引数…>; printf '\036%s:%s\036\n' <nonce> $?`
     引数はすべてシングルクォートで囲み、中の `'` は `'\''` に置き換える。
  3. `terminal.data` を読み、`\036<nonce>:<終了コード>\036` が来るまで待つ（最大 15 秒）。nonce は毎回の乱数。
     エコーされたコマンド行は `%s` のままなので、本物の結果と見分けられる。標準エラーの最後の 1 行を理由として取る
  4. `terminal.close`
  スクリプトの中身と終了コードの意味は [contracts/file-ops-shell.md](./contracts/file-ops-shell.md)。
- **外へ出ない守り（二重）**:
  - アプリ: 名前の検証（`/`・`..`・`.`・空・NUL と制御文字・255 バイト超を断る）。対象は `sessions.files.list` が返した作業フォルダの `root` と、
    ツリーの相対パスだけから組み立てる。
  - シェル: `cd -P -- "$root"` で実体のパスを取り、親のフォルダも `cd -P` で実体に解決して、`root` の実体のパスの下にあるか確かめる。
    外を指すシンボリックリンクの先なら終了コード 3 で断る。作成は `set -C`（上書きしない）と `mkdir`、名前の変更は対象が無いことを確かめてから `mv -n`、
    削除はファイルは `rm -f --`、フォルダは `rm -rf --`。
- **使えるか**: `TerminalRepository.availability()`（`operator.admin` と Gateway のターミナルの許可）が null のときだけ操作を出す（FR-066）。
  `terminal.open` の結果の `confined` が true（サンドボックスのシェル）で `root` に入れないときは、終了コード 4 で「この Gateway ではできない」と示す。
- **同時実行**: 1 つずつ（`Mutex`）。操作の間はツリーのその行に小さな進み具合を出す。
- **結果の確かめ**: 終了コードに関わらず、親のフォルダを `sessions.files.list` で取り直してツリーに反映する（FR-067。接続が切れたときもこれで確かめる）。
- **Rationale**: Gateway にファイルの作成・削除の API が無い。ターミナルは既にアプリが使っている権限の範囲で、新しい権限を求めない。
- **Alternatives**: ユーザーのターミナルに打ち込む → 利用者のシェルの状態（cwd・履歴）を汚す。`terminal.upload` → 一時フォルダに置くだけで、任意の場所に作れない。

## R8. 秘密の値を含む下書き

- **Decision**: 名前が `.env`・`.env.*`・`*.pem`・`*.key`・`*secret*`・`credentials*` などに当たるファイルの下書きは、Room の `file_drafts` ではなく
  メモリだけに持つ（プロセスの終了で消える）。そのファイルを開いているあいだ、保存していない変更があれば、画面を離れるときに確認を出す（002 FR-010 のまま）。
- **Rationale**: 既存の `data_extraction_rules.xml` は `database` をクラウドのバックアップからは外しているが、機種変更の引き継ぎ（device-transfer）には含める。
  秘密の値を端末の DB に平文で残さないのが最も単純で確実（FR-068）。
- **Alternatives**: device-transfer から `database` を外す → 会話の下書きなど他のものまで引き継げなくなる。暗号化して保存 → 鍵の管理が増える（憲章 VI）。
- **spec との差**: spec FR-068 は「バックアップと引き継ぎに含めない」。メモリだけにするとプロセスの終了で消えるため、
  002 FR-011（下書きはプロセスの終了を越えて残す）の例外になる。秘密の値を優先する判断として tasks で明記する。

## R9. 特殊キーの行の並び

- **Decision**: `Esc · Tab · Ctrl · ^C · ← ↑ ↓ → · 貼り付け`。行が端末の幅に収まらないときは横にスクロール（`horizontalScroll`）。
  「^C」は文字のボタン（他のキーと同じ `TextKey`）、貼り付けは Hugeicons のアイコン（`clipboard` を足す）。シェルが終わっている・つながっていないときは押せない見た目。
- **Rationale**: 止める操作は Ctrl の隣に置くと、Ctrl+C の近道だと分かる。
