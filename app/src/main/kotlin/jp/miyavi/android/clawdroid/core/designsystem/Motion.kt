package jp.miyavi.android.clawdroid.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * アプリ全体の動き。Material 3 Expressive のばね（位置・大きさは少し弾み、色・不透明度は弾まない）。
 * 所要時間は決めず、すべて [MaterialTheme.motionScheme] のトークンから取る（AGENTS.md 2.8）。
 */
val AppMotionScheme: MotionScheme = MotionScheme.expressive()

/** 画面全体が動く遷移用。弾むと端に下の画面がのぞくので、行き過ぎのほとんどない標準のばねを使う。 */
val ScreenMotionScheme: MotionScheme = MotionScheme.standard()

/** 画像を開くときの始まりの大きさ。 */
const val IMAGE_OPEN_START_SCALE = 0.92f

/** 下の画面が暗くなる最大の濃さ（画面遷移）。 */
const val SCREEN_DIM_ALPHA = 0.32f

/** リストの中身が変わったとき（追加・削除・並べ替え）だけ項目を動かす。スクロールでの出現演出はしない。 */
@Composable
fun LazyItemScope.motionItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** その場で縦に開く部分の出方。出るときは丁寧に、消えるときは手早く。 */
@Composable
fun expandEnter(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return expandVertically(animationSpec = motion.defaultSpatialSpec()) + fadeIn(animationSpec = motion.defaultEffectsSpec())
}

@Composable
fun shrinkExit(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return shrinkVertically(animationSpec = motion.fastSpatialSpec()) + fadeOut(animationSpec = motion.fastEffectsSpec())
}
