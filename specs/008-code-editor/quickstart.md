# Quickstart: アプリに入れるエディター

## 準備

- 実機（Android 13 以上）。日本語の Gboard を入れておく
- `./gradlew :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/*.apk`
- 試すファイル（作業フォルダに置く・端末の Download に置く）:
  - `big.kt`: 1 万行・約 1.5 MB の Kotlin（`for i in $(seq 1 400); do cat app/src/main/kotlin/jp/miyavi/android/sumahoclaw/feature/chat/ChatViewModel.kt; done | head -10000 > big.kt`）
  - `mid.kt`: 3,000 行・約 200 KB（作業フォルダの 256 KiB 以下）
  - `long-line.js`: 1 行 50 万文字（minify された js）
  - `sjis-crlf.txt`: Shift_JIS・CRLF の日本語（`printf '日本語①\r\n髙橋\r\n' | iconv -f UTF-8 -t CP932 > sjis-crlf.txt`）
  - `euc.txt`: EUC-JP の日本語、`bom.md`: UTF-8 の BOM 付き、`utf16.txt`: UTF-16LE の BOM 付き

## 関門（実装の最初に流す。通らなければ research R1 の退路へ）

| # | 確かめること | 通る条件 |
|---|---|---|
| G1 | 日本語の入力（Gboard）: ローマ字入力で変換・確定・文節の選び直し（候補の帯から）・変換中の削除・予測の挿入・音声入力 | 普通の入力欄と同じ結果。文字が重複・消失しない |
| G2 | `big.kt` を開き、色分けを付けたまま、行の途中で 30 秒打ち続ける・速くスクロールする | 打鍵の遅れが目に見えない。スクロールがカクつかない。色分けを止める行数の境目をここで決める |
| G3 | `long-line.js` を折り返しオフで開き、横にスクロールして打つ | 固まらない |
| G4 | 実機で `windows-31j`・`Shift_JIS`・`EUC-JP` の `Charset` が使えるか（デバッグ版の一時的なログで確かめる） | 使える。使えなければ R5 の候補から外す |
| G5 | 選択のつまみ・拡大鏡・長押しのメニュー（切り取り・コピー・貼り付け・すべて選択） | 普通の入力欄と同じように使える |
| G6 | 上の TopAppBar・下の情報の帯（ガラス）の下にスクロールした本文が、ガラス / ぼかしの両方の設定で透けて見えるか | 他の画面のガラスと同じに見える。透けなければ、本文の上下に余白を取ってガラスの下に本文を通さない |

## 受け入れの確かめ方（spec の US ごと）

1. **US1**: 作業フォルダで `mid.kt` を開いて 200 行目までスクロール →「編集」→ 200 行目あたりが出る → 1 文字直して保存 → 戻る → ビューアに直した内容。PC の Control UI でも変わっている
2. **US1-5・6**: 直して戻る → 確認が出る。PC で同じファイルを変えてから保存 → 衝突の確認
3. **US1-7**: 直したまま「開発者向けオプション → バックグラウンドプロセスの上限 → なし」で裏に回して戻る → 本文とカーソルが戻る
4. **US2**: kt・js・md・txt・拡張子なしで、色分け・言語の選び直し・折り返しの切り替え（コードは既定オフ、md は既定オン）・帯の数字。「👨‍👩‍👧が」の文字数が 2、選択の文字数
5. **US3**: 添付・端末の `sjis-crlf.txt`・`euc.txt`・`bom.md`・`utf16.txt` を開いて帯を見る → 1 文字直して保存 → `cmp` で直した箇所以外が同じ（SC-004）。Shift_JIS に絵文字を打って保存 → 保存できない理由とカーソルの移動
6. **US4**: ファイル管理アプリ（Files by Google）で `sjis-crlf.txt` →「アプリで開く」→ SumahoClaw エディター → 直して保存 → 開き直して確かめる。Gateway の接続を切っても同じ。読むだけの提供元（例: メールの添付）からは「別の名前で保存」
7. **US5**: 会話の md の添付 →「編集」→ 保存 → Downloads に元の名前で 1 つ。もう一度保存 → 増えない。コピーしたテキストのタイルを直して保存 → タイルの文字数が変わる → 送った .txt に反映
8. **作業フォルダの上限**: `mid.kt` に貼り付けて 256 KiB を超えさせて保存 → 理由が出て、保存されない
9. **見た目**: ライト / ダーク × スマホ / タブレット × 文字 100% / 200%（AGENTS.md 4）。アニメーター再生時間スケール 5x で、開く・戻る・予測型「戻る」の途中止め
10. **単体テスト**: `./gradlew :app:testDebugUnitTest`（下の一覧）

## 単体テスト（足すもの）

- `TextDecodingTest`: BOM の 3 種・UTF-8・Shift_JIS・EUC-JP・判定できない・NUL・改行の判定（LF / CRLF / CR / 混在）・書き戻しのバイトが同じ・書けない文字の位置
- `StatusCounterTest`: 書記素の数え方（絵文字の ZWJ・結合文字・改行を数えない）・行と列
- `EditorIntentsTest`: extras の書き読み、外からの Intent で内部の extras を読まない
- `EditorViewModelTest`: 読み込み → 編集 → 保存（成功・衝突・無い・失敗・上限）、下書きの復元、BOM の外しと戻し
- `EditorDraftMigrationTest`: データベースの版 2 → 3 で既存の下書きが残る
- `HighlightBridgeTest`: `TokenSpan` → 行・列の区間への変換（行をまたぐコメント・最後の行）
