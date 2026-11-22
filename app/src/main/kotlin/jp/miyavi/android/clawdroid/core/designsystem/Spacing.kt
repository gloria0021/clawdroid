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
    val floatingButtonSize = 48.dp
    val composerMaxWidth = 720.dp
    /** 入力欄が伸びる上限の行数ぶんの高さ。 */
    val composerMaxHeight = 160.dp
    val tabPillHeight = 32.dp
    val unreadDot = 8.dp
    val emptyStateIcon = 72.dp
    val avatar = 24.dp
    val usageBarHeight = 8.dp
    /** 入力欄のモデルのピルの最大幅（長いモデル名は省略記号）。 */
    val modelPillMaxWidth = 200.dp
}
