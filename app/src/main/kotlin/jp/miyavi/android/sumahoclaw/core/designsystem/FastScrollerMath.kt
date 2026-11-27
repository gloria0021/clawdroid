package jp.miyavi.android.sumahoclaw.core.designsystem

import kotlin.math.roundToInt

/** 高速スクロールバーの位置と項目の番号 の対応。ピクセルではなく index 基準にして、項目の高さに左右されないようにする。 */
object FastScrollerMath {
    fun indexForFraction(fraction: Float, count: Int): Int {
        if (count <= 1) return 0
        return (fraction.coerceIn(0f, 1f) * (count - 1)).roundToInt()
    }

    fun fractionForIndex(index: Int, count: Int): Float {
        if (count <= 1) return 0f
        return index.coerceIn(0, count - 1).toFloat() / (count - 1)
    }

    /** トラック上の位置（つまみの上端）から割合を求める。 */
    fun fractionForOffset(offsetPx: Float, trackLengthPx: Float): Float =
        if (trackLengthPx <= 0f) 0f else (offsetPx / trackLengthPx).coerceIn(0f, 1f)
}
