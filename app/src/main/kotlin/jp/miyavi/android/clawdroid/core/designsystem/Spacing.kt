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
    val emptyStateIcon = 72.dp
    val avatar = 24.dp
    /** キャラクターの大きいアイコン（avatar の 2 倍）。サイドパネルのタブと会話の応答で使う。 */
    val avatarLarge = 48.dp
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
}
