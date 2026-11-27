# AGENTS.md — SumahoClaw

このリポジトリで作業するすべてのエージェント・開発者向けの指示。

## 優先順位と読む範囲

- **見た目と動きの正本は、このファイルと `docs/design/screens.md`**。仕様書（`specs/`）と食い違ったらこちらに従い、食い違いを報告する
- このファイルは全体の決まり。画面ごとの決まり（サイドパネル・会話の画面・入力欄・ファイルビューアなど）は `docs/design/screens.md` にある。その画面の見た目や動きを変えるときは必ず読む
- ここに無い見た目の判断は、勝手に決めずに既存の部品に合わせるか、質問する
- 数値（不透明度・寸法）の正は `core/designsystem/` のコード（`Glass.kt`・`Spacing.kt`・`Theme.kt`）。このファイルの数値と食い違ったら報告する

## 0. 技術スタック（版は `gradle/libs.versions.toml` が正）

| 領域 | 採用するもの |
|---|---|
| UI | Jetpack Compose（BOM）＋ **Material 3 Expressive**（`material3` 1.5 系。動きは `MotionScheme.expressive()`）、Material 3 Adaptive（list-detail） |
| 画面遷移 | Navigation 3（`NavDisplay`、`ListDetailSceneStrategy`） |
| 素材表現 | Haze（`haze` / `haze-blur` / `haze-glass` / `haze-glass-material3`）、MaterialKolor（カスタム色の配色生成） |
| アイコン | Hugeicons Stroke Rounded（必要なものだけ VectorDrawable にして `res/drawable/ic_huge_*.xml`） |

数行で書ける処理のために依存を足さない。

---

## 1. Jetpack Compose の書き方

- 状態は持ち上げる。部品は値とラムダを受け取り、ViewModel を直接受け取らない。ViewModel をつなぐ部分と、状態とコールバックだけを受ける部分（`*Content`）を分ける
- `LazyColumn` / `LazyVerticalGrid` の `items` には必ず安定した `key` を付ける
- 重い計算（テキストの解析・日付の整形・索引作り）は ViewModel か `remember(key)` で一度だけ行う。項目の Composable の中で正規表現を回さない
- UI 状態のクラスは不変（`data class` ＋ `val`、`List` は作り直して渡す）。strong skipping を前提に、ラムダの中で可変の状態を捕まえない
- 毎フレーム変わる値（スクロール・アニメーション）は `derivedStateOf` か、`graphicsLayer { }`・`drawBehind { }`・`Modifier.offset { }` の**ラムダの中で読む**。`Modifier.alpha(value)`・`Modifier.offset(x.dp)` のように引数で渡すと毎フレーム再構成になる
- edge-to-edge（`enableEdgeToEdge()`）が前提。余白は `Scaffold` の `innerPadding` と `WindowInsets` から取り、固定の高さでシステムバーを避けない
- 戻る操作は Navigation 3 と `BackHandler` に任せ、予測型「戻る」を壊す独自の処理を書かない
- 大画面は Material 3 Adaptive の list-detail で対応する。画面サイズの分岐を自前で書かない
- アニメーションの `label = "…"` は camelCase の英字の識別名（文言ではない）

---

## 2. デザイン

### 2.1 原則

1. **主役はコンテンツ**。装飾・色・アイコンは、内容を読む・操作するのを助けるときだけ使う
2. **シンプルなリスト**。情報密度を保つ。カードやタイル型にしない
3. **Material 3 Expressive の標準部品とデザイントークンだけで作る**。値を直書きしない。Expressive は Material 3 の置き換えではなく拡張で、同じ `androidx.compose.material3` に入っている
4. **迷ったら引く**。要素を足すより削る
5. **新しい Android の体験を優先する**（edge-to-edge、予測型「戻る」、Dynamic Color、アプリ単位の言語、大画面の 2 ペイン）。読みやすさ・アクセシビリティ・性能・対応端末との互換性を確かめる

「デザイントークン」は、色・文字・形・動きの値に付いた名前（`colorScheme.primary`、`typography.bodyLarge`、`motionScheme.defaultSpatialSpec()` など）。値そのものは書かず、名前で使う。

### 2.2 色

- 色は必ず `MaterialTheme.colorScheme.*` から取る。`Color(0xFF...)` や `Color.Red` の直書きは禁止（テーマ定義ファイルを除く）
- ベーステーマは 固定色（OpenClaw・Monochrome）/ Dynamic Color / カスタム色（MaterialKolor で生成。RGB のスライダーとカラーコード）から選べ、外観（システム / ライト / ダーク）とは独立。固定色と配色生成は `core/designsystem/Theme.kt` に集める
- ダークテーマの背景は `surface`。純黒（#000000）は使わない
- 意味のない色分けはしない。グラデーションと影で色を付けるのはガラス風の面（2.10）だけ（画面ごとの例外は `docs/design/screens.md`）

| 用途 | トークン |
|---|---|
| 背景 | `surface` |
| 本文 | `onSurface` |
| メタ情報（名前・日時・補足） | `onSurfaceVariant` |
| リンク・強調したい識別子、新着・未読の目印 | `primary` |
| 本文の下地（角丸の面） | `surface` に `onSurface` を重ねた色（ライト 3% / ダーク 5%） |
| 補助的な目印 | `tertiary` |
| 件数バッジ・チップ | `secondaryContainer` / `onSecondaryContainer` |
| 検索で一致した語 | 背景 `tertiaryContainer` / 文字 `onTertiaryContainer` |
| エラー | `error`、ラベルは `errorContainer` / `onErrorContainer` |
| 区切り線 | `outlineVariant`（ガラスのメニューの中だけ `outline`。`GlassMenuDivider()`） |
| 無効・折りたたみ | `onSurfaceVariant`（不透明度 0.6 まで） |

### 2.3 文字

- `MaterialTheme.typography.*` だけを使う。`fontSize = 13.sp` のような直書きは禁止
- 端末の文字サイズ設定を尊重する。コードは等幅フォント
- 太字は見出しだけ。本文中の強調に太字を使わない

| 要素 | スタイル |
|---|---|
| 本文 | `bodyLarge`（行の高さは文字サイズ × 1.5） |
| リスト項目のタイトル | `titleMedium`（2 行まで、はみ出しは省略記号） |
| リスト項目のメタ行、日時・チップ・詳細 | `labelMedium` |
| TopAppBar のタイトル | `titleMedium`（2 行まで） |
| ヘッダの識別子 | `labelLarge` |
| 一覧のカテゴリ見出し | `titleSmall` |
| 設定のセクション見出し | `labelLarge` / `primary` |

### 2.4 余白・大きさ（4dp グリッド）

- 余白は 4 の倍数だけ。`core/designsystem/Spacing.kt` の `Spacing.xs = 4`・`s = 8`・`m = 12`・`l = 16`・`xl = 24` と部品の寸法 `Dimens.*` を使う
- 画面の左右は 16dp。大画面では本文の幅を 720dp までにして中央に寄せる
- 本文を角丸の面（`shapes.large`）に載せるときは、内側を左右 12dp・上下 8dp。項目どうしは余白で分け、区切り線を引かない
- リストの行は上下 12dp。タイトルとメタ行の間は 4dp
- 押せる範囲はすべて 48dp 以上（ヘッダ行とチップだけ 32dp）

### 2.5 形・高さ・影

- 角の丸みは `MaterialTheme.shapes.*`（シート = `extraLarge` の上側、ダイアログ = `extraLarge`、バッジ = `full`）
- リストの項目は角丸にせず、影も付けない。**`Card` は使わない**（本文の下地の角丸の面は、背景色だけで描く）
- 階層は影ではなく M3 の tonal elevation（スクロール時の TopAppBar の色の変化）で表す。影を付けるのは、会話の上に浮く入力欄（6dp）と会話の中の画像（3dp）だけ
- ボトムシートは共通部品 1 つで描く（ガラス風の面。高さは TopAppBar より上に伸ばさない）。読む・書く中身は、シートより明るい不透明な面（ライト `surfaceContainerLowest`、ダーク `surfaceContainerHighest`）に載せ、長ければその面の中だけをスクロールさせる。待っているあいだも同じ面の中にプレースホルダーを置く

### 2.6 アイコン

- Hugeicons の無料 **Stroke Rounded** だけを使う。Material Icons や他のセットと混ぜない
- 公式データから必要なものだけを `res/drawable/ic_huge_<名前>.xml` にし、`painterResource(R.drawable.ic_huge_*)` で使う。アイコンフォントやライブラリ全体は入れない。ライセンス（MIT）は `assets/licenses/` に同梱する
- 色は `LocalContentColor`（`Icon` の既定）に任せ、XML に色を固定しない。線の太さと角丸は公式データのまま
- TopAppBar のアイコンは最大 2 つ ＋ ⋮。それ以外は ⋮ のメニューに入れる
- アイコンだけのボタンには `contentDescription`（`strings.xml` の `*_cd`）を付ける。飾りのアイコンは `null`

### 2.7 部品の使い分け

| 用途 | 使う | 使わない |
|---|---|---|
| 下部の切り替え | 画面下部に浮くガラス風のピル型タブ | BottomNavigation / NavigationBar |
| 短い補助表示 | `ModalBottomSheet`（多段はシートの中身を積み重ねる） | Dialog、Popup |
| 中身の多い詳細（設定の編集・履歴など） | 新しい画面でよい | |
| 入力欄の拡張 | `ModalBottomSheet`（TopAppBar の下まで広げられる） | 新しい画面 |
| 画像 | 全画面のオーバーレイ | 新しい画面、外部アプリへの丸投げ |
| 確認 | `AlertDialog` | |
| 主要な操作・検索 | 画面下部に浮くガラス風のピル。左に主操作、`VerticalDivider` で区切って右に道具。検索中はピルが検索欄と前後の送りに形を変える | 常に出ているツールバー、FAB |
| メニュー（⋮・長押し）・ポップアップ | ガラス風のドロップダウン（角丸 `shapes.large`、影なし） | 素の `DropdownMenu` |
| 通知・失敗 | `SnackbarHostState` で出し、ガラス風のピルで描く Snackbar。再試行の操作付き | Toast、M3 標準の反転色の Snackbar |
| ダウンロード・アップロードの進み | 画面上部のガラス風の帯（`DownloadBanner` / `UploadBanner`。進み・中止・結果）。会話の添付の保存も `DownloadCenter` で動かす | 一覧の行の中だけで動く処理 |
| 設定 | `ListItem` ＋ `Switch` / `Slider`。1 つを選ぶ切り替えは `ConnectedChoiceRow`（`ToggleButton` ＋ `ButtonGroup`）。1 画面に縦に並べる | 階層の深いサブ画面、`SegmentedButton` |

- 同じ役割に無印の M3 と Expressive の部品があれば Expressive を使う（例: `ToggleButton` / `ButtonGroup`、`LoadingIndicator`）。`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` は付けてよい
- ただしこの表と 2.10 が先。表で「使わない」とした役割の部品（Expressive の浮くツールバー、FAB など）は使わず、ガラスのピルで描く
- 標準部品で表せないものは、ガラス風の面（2.10）と高速スクロールバー以外に作らない。新しく自作の部品が要るときは、作る前に相談する

### 2.8 動き

**考え方**

0. **動きの心地よさを最優先する**。重い処理（推論・大きな一覧の組み立て・解析）は動きと重ねず、動き終わってから始める。機能や見た目と滑らかさがぶつかったら、滑らかさを取る
1. **動きは説明のために使う**。どこから来て、どこへ行き、何が変わったかが伝わるときだけ動かす。飾りの動き、待たせる動きは入れない
2. **操作を待たせない**。動きの途中でも次の操作を受け付ける。状態は先に確定させ、見た目があとから追いつく
3. **ばねで動かす**。固定時間の `tween` は使わない（途中で向きを変えると速度が途切れる）
4. **位置・大きさ・形は弾んでよい（spatial）。色・不透明度は弾ませない（effects）**
5. **出るときは丁寧に、消えるときは手早く**。出る側は `default`、消える側は `fast`
6. **動かす範囲が大きいほど控えめに**。小さな部品は弾んでよい。画面全体の移動は弾ませない
7. **読んでいる最中は動かさない**。動かすのは利用者の操作への応答だけ

**動きのデザイントークン**（`MaterialTheme.motionScheme`。所要時間・easing・ばねの値を自分で決めない）

| 変わるもの | 使う | 例 |
|---|---|---|
| 位置・大きさ・形（小さな部品、押した反応） | `fastSpatialSpec()` | スクロールバーの太さ |
| 位置・大きさ・形（通常） | `defaultSpatialSpec()` | タブの選択面、リスト項目の移動、開閉、画像の拡大 |
| 位置・大きさ・形（大きく・ゆっくり） | `slowSpatialSpec()` | 必要になるまで使わない |
| 色・不透明度（すぐ反応させる） | `fastEffectsSpec()` | 文字色の切り替え、消えるフェード |
| 色・不透明度（通常） | `defaultEffectsSpec()` | 出るフェード |
| 色・不透明度（余韻を残す） | `slowEffectsSpec()` | スクロールバーが消えるとき |

- アプリ全体は Expressive のスキーム。画面全体が動く遷移だけは、弾むと端に下の画面がのぞくので標準のスキームを使う
- 大きさの割合（画像を開くときの 0.92 倍など）は名前を付けた定数にする。時間は書かない

**API の選び方**

| やりたいこと | 使う |
|---|---|
| 1 つの値を状態に追従させる | `animate*AsState(target, motionScheme.…)` |
| 出す・消す | `AnimatedVisibility(enter, exit)` |
| 親と子で別の動き（背景はフェード、中身は拡大） | `AnimatedVisibility` の中で `Modifier.animateEnterExit()` |
| 消え終わってから閉じる（Dialog・オーバーレイ） | `MutableTransitionState` を持ち、`isIdle && !currentState` で `onDismiss` |
| リストの追加・削除・並べ替え | トークンを渡した `animateItem`（安定した `key` が要る） |
| 始まりの値を決める・ジェスチャーとつなぐ | `Animatable`（`snapTo` / `animateTo`）、一度きりなら `animate(from, to, spec) { }` |
| 画面遷移・予測型「戻る」 | `NavDisplay` の `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` |
| 位置の移動 | 利用者が押した移動は `animateScrollToItem`、復元や表示の切り替えは `scrollToItem` |

**軽く保つ**

- 不透明度・拡大・移動は `graphicsLayer` で行う（レイアウトを変えない）。大きさを毎フレーム変えるのは、周りの配置も動かしたいときだけ
- 最初の表示で動かさない（位置が分かってから `animate*AsState` を作るか、`Animatable` の初期値を出し分ける）
- 重いリストどうしで `AnimatedContent` / `Crossfade` を使わない。新しい側だけをフェードで出す
- 押した反応（ripple）を遅らせない。`clickable` の前に重い処理を置かない
- 画面が入ってくる途中に頼まれた遷移（戻る・開く）は、入り終わってから実行する（Navigation 3 は逆向きの遷移が割り込むと重なり順が狂う）

**決まった動き**

- 画面遷移: 上の画面が右から重なり、下の画面はその場で暗くなる。予測型「戻る」では上の画面が右へ動いて下の画面が現れる。シートとメニューは M3 標準の出入り
- プレースホルダー: 取得中は中身の形の帯に光を流す（見えているあいだだけ）。そろったら、プレースホルダーを薄くしながら本物をフェードで重ねる

**しないこと**

- スクロールで入ってくる項目の出現演出
- 位置の復元や、表示の切り替えに伴う先頭への移動を動かすこと（一瞬で行う）
- 意味のない触覚フィードバック
- 動きで色を付けること、ループし続ける演出（読み込み表示と、作業中の顔のアメーバだけは可。アメーバは端末の「アニメーションを削除」で止める）

**確かめ方**: 実機で連打・途中での取り消し・予測型「戻る」を途中で止める。開発者向けオプションの「アニメーター再生時間スケール」を 5x にすると、飛び・ちらつきが見つけやすい。オフでも操作できることも確かめる。

### 2.9 状態の保持

- 読んでいた位置・下書きは、プロセスの終了と構成の変更（言語・回転・折りたたみ）を越えて残す（ViewModel ／ `rememberSaveable` ／ 永続化）

### 2.10 ガラス風の面（Haze）

- Haze で透かすのは**画面の上に浮く面だけ**（TopAppBar、下部のピル、シート、メニュー、Snackbar、接続の状態のお知らせ、スクロールバーの吹き出し）。リストの項目や本文には使わない
- 実装は `core/designsystem/Glass.kt` の共通部品（`Modifier.hazeSurface()` / `Modifier.sheetHazeSurface()` / 共通の TopAppBar）に集める。Haze の API を画面から直接呼ばない
- 透かす元（リストなど）に `Modifier.hazeSource(hazeState)` を付ける。`HazeState` は画面ごとに 1 つ
- 透かし方は **ガラス**（既定。ぼかさず屈折だけ）/ **ぼかし** から設定で選べる。強さは設定で 0〜10（既定はガラスの屈折 6、ぼかし 3。ぼかしの 10 は 48dp）。不透明度は面ごとの固定値:

| 面 | ガラス | ぼかし | 定数 |
|---|---|---|---|
| シート | 90% | 30% | `SHEET_OPACITY` |
| メニュー・ポップアップ | 80% | 80% | `MENU_OPACITY` |
| 入力欄（会話の入力欄・検索欄） | 80% | 80% | `FIELD_OPACITY` |
| 接続の状態のお知らせ | 80% | 80% | `NOTICE_OPACITY` |
| タイトルバー | 20% | 20% | `TITLE_BAR_OPACITY` |
| それ以外 | 50% | 30% | `SURFACE_OPACITY` |

- **色味はどの面も 1 種類**: テーマカラー（`primary`）の色相のまま彩度を 50% に落とし、`scrim` を 30% 混ぜたくすみ色を、背景色（`surface`）の上に 20% 重ねる。面ごとの違いは上の表の不透明度だけ。会話の入力欄だけは白い面（ライト `surfaceContainerLowest`、ダーク `surfaceContainerHighest`）。文字のコントラストは WCAG AA を割らない
- 横いっぱいの帯（タイトルバー）は下の縁だけで屈折させ、左右と上は画面の外まで広げて描く（画面の端で屈折させると、外の何も無い所が透けて見える）
- Haze が作る影・グラデーション・屈折は、ガラス風の面でだけ許す

### 2.11 アクセシビリティ

- 文字を 200% にしても切れたり重なったりしない
- 文字と背景のコントラストは WCAG AA 以上（トークンどおりなら満たせる）
- 自作のスクロールバーには TalkBack 用の位置情報と移動の操作を付ける
- 長押しで開く操作には `onLongClickLabel` を付ける

---

## 3. 禁止事項

- 色・文字の大きさ・余白の直書き
- 利用者向け文言の直書き（`strings.xml` に置く）。書くのは日本語（`app/src/main/res/values/`）だけ。文言を足す・変える・消すときは、同じ変更で `scripts/translate-strings.py` を流し、`app/src/l10n/` の訳も一緒にコミットする（Debug でも訳を使う。訳は手で直さない）
- `Card`、純黒の背景、ガラス風の面以外の影・グラデーション（画面ごとの例外は `docs/design/screens.md` に書いたものだけ）
- ガラス風の面ごとに別の色味を付けること
- 別のスタイルのアイコンを混ぜること、TopAppBar に ⋮ 以外のアイコンを 3 つ以上置くこと

## 4. 見た目を変えたときの確認

- [ ] ライト/ダーク × スマホ/タブレット × 文字 100%/200% で崩れていない
- [ ] 直書きした色・大きさ・余白がない（`grep -rn "Color(0x\|\.sp\b" app/src/main` で、テーマ・Spacing 以外に該当なし）
- [ ] 「3. 禁止事項」に当てはまるものがない
- [ ] 実機で長い一覧をスクロールし、カクつかない
- [ ] 画面ごとの決まりを変えたら `docs/design/screens.md` も直した

## 5. リリースとストア申請

- 手順は `scripts/release.sh`（署名した APK を GitHub Release に、AAB を手元に作る。AAB は Play Console に上げる）、方針は `specs/003-play-store-release/spec.md`
- 公開しない申請用の情報の案内は、リポジトリ直下の `store-release.md`（`.gitignore` で除外。手元にだけある）。リリース・ストア申請の作業では最初に読む。無ければ作業を止めて聞く
- `store-release.md` とその案内先の中身を、このリポジトリのファイル・コミット・Issue・PR に写さない
