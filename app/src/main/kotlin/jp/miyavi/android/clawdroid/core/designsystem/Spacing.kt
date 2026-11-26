package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.ui.unit.dp

/** 余白は 4dp グリッドのこの定数だけを使う（AGENTS.md 2.4）。 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 余白以外の決まった寸法。 */
object Dimens {
    val minTouchTarget = 48.dp
    /** M3 の 2 行の一覧の最低の高さ。 */
    val listItemTwoLine = 64.dp
    /** ヘッダ行とチップのタップ領域（AGENTS.md 2.4 の例外）。 */
    val compactTouchTarget = 32.dp
    val maxReadingWidth = 720.dp
    val sidebarWidth = 320.dp
    val sidebarAppIcon = compactTouchTarget * 1.2f
    val floatingButtonSize = 48.dp
    val composerMaxWidth = 720.dp
    /** 入力欄が伸びる上限の行数ぶんの高さ。 */
    val composerMaxHeight = 160.dp
    /** 入力欄を浮かせる影の高さ。 */
    val composerElevation = 6.dp
    /** 会話の中の画像を浮かせる影の高さ。 */
    val imageElevation = 3.dp
    val tabPillHeight = 32.dp
    val unreadDot = 8.dp
    /** サイドパネルの行の「実行中」の小さな回るプログレスと、その線の太さ。 */
    val runningIndicator = 12.dp
    val runningIndicatorStroke = 2.dp
    val emptyStateIcon = 72.dp
    /** 接続の画面のマスコットの丸。 */
    val onboardingMascot = 96.dp
    val avatar = 24.dp
    /** キャラクターの大きいアイコン（avatar の 2 倍）。サイドパネルのタブと会話の応答で使う。 */
    val avatarLarge = 48.dp
    /** 細い線（作業中の顔を囲むアメーバの外周の枠）。 */
    val hairline = 1.dp
    /** 作業中の顔のアメーバを、デザイン資料の寸法より外へ広げる幅（マスター指定 2026-11-26）。 */
    val amoebaReach = 1.dp
    /** M3 の一覧の先頭の丸（40dp）。 */
    val listLeading = 40.dp
    /** 応答の下の操作（返信・コピー）の小さめのアイコン。 */
    val smallIcon = 18.dp
    /** 設定のメニューの最大幅。 */
    val settingMenuMaxWidth = 320.dp
    /** 設定のメニューの最大の高さ（超えるとメニューの中でスクロール）。 */
    val settingMenuMaxHeight = 320.dp
    val usageBarHeight = 8.dp
    /** 添付画像を読み込むあいだの面の大きさ。 */
    val imagePlaceholder = 160.dp
    /** 会話の中の画像のプレビューの最大の辺。 */
    val messageImageMax = 240.dp
    /** 入力欄の中の添付のサムネイル。 */
    val attachmentThumbnail = 64.dp
    /** Markdown の表の列の最低幅（これより狭くなるなら表を横にスクロール）。 */
    val tableCellMinWidth = 160.dp
    /** ダイアログの最大幅（M3 の基本のダイアログ）。 */
    val dialogMaxWidth = 560.dp
    /** サイドパネルの行のブランチ名の最大幅（名前を押しつぶさない）。 */
    val branchLabelMaxWidth = 140.dp
    /** 絵文字 1 文字の入力欄の幅。 */
    val emojiField = 72.dp
    /** 添付を外す × の丸と、その中のアイコン。 */
    val attachmentRemove = 24.dp
    val attachmentRemoveIcon = 16.dp
    /** 複数の画像を 1 行に並べるときのサムネイルの高さ。 */
    val messageImageRowHeight = 160.dp
    /** 入力欄のモデルのピルの最大幅（長いモデル名は省略記号）。 */
    val modelPillMaxWidth = 200.dp
    /** 入力欄の上に出すスラッシュコマンドの候補の最大の高さ（超えると候補の中でスクロール）。 */
    val slashMenuMaxHeight = 280.dp
    /** 聞き取り中の声の波形の高さ・線の太さ・間隔。 */
    val voiceWaveHeight = 32.dp
    val voiceWaveBar = 3.dp
    val voiceWaveGap = 3.dp
    /** 高速スクロールバーのつまみ（ふだん / つかんだとき）と長さ。 */
    val scrollbarThin = 4.dp
    val scrollbarThick = 8.dp
    val scrollbarThumbLength = 48.dp
    /** カスタムの色のスライダーの最大の高さ（超えると中でスクロール）。 */
    val colorControlsMaxHeight = 240.dp
    /** フォルダ選択の一覧の最大の高さ（超えると中でスクロール）。 */
    val folderBrowserMaxHeight = 420.dp
    /** 使用量の帯の、使った所の下の縁の線の太さ。 */
    val usageMeterEdge = 4.dp
    /** サイドパネルの頭の看板の高さ（ステータスバーの下から）。 */
    val agentHeroHeight = 180.dp
    /** Gateway につながっていないときに会話の画面の真ん中に出すマスコットの大きさ。 */
    val offlineMascot = 96.dp
}

/** 使えない（Gateway につながっていないなど）部品の不透明度（AGENTS.md 2.2 の無効・折りたたみ 0.6）。 */
const val DisabledAlpha = 0.6f
