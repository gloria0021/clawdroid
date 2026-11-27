package jp.miyavi.android.sumahoclaw.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.sumahoclaw.R
import kotlinx.coroutines.delay

/**
 * 設定を変えたときの印（マスター指定 2026-11-26: 反映が遅い回線でも、切り替わったかが分かるように）。
 * ふだんは [idle]（∨ や設定のアイコン）、保存中はその場所で M3 Expressive の読み込み表示、保存できたら一瞬チェック。
 * 同じ大きさで入れ替えるだけなので、行の高さも幅も動かない。ダイアログは出さない。
 *
 * @param savedTick 保存できた回数。増えたときだけチェックを出す（画面を開き直したときや、別の項目の保存では出さない）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveStateIcon(saving: Boolean, savedTick: Int, modifier: Modifier = Modifier, idle: @Composable () -> Unit) {
    var seen by rememberSaveable { mutableIntStateOf(savedTick) }
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(savedTick) {
        if (savedTick > seen) {
            seen = savedTick
            confirming = true
            delay(SAVED_CHECK_HOLD_MS)
            confirming = false
        }
    }
    val phase = when {
        saving -> SavePhase.SAVING
        confirming -> SavePhase.SAVED
        else -> SavePhase.IDLE
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = SWAP_START_SCALE)) togetherWith
                fadeOut(motion.fastEffectsSpec())
        },
        contentAlignment = Alignment.Center,
        label = "saveStateIcon",
        modifier = modifier,
    ) { p ->
        when (p) {
            SavePhase.SAVING -> LoadingIndicator(Modifier.fillMaxSize())
            SavePhase.SAVED -> Icon(
                painterResource(R.drawable.ic_huge_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
            SavePhase.IDLE -> idle()
        }
    }
}

private enum class SavePhase { IDLE, SAVING, SAVED }

/** 入れ替わるアイコンが出るときの最初の大きさ。 */
private const val SWAP_START_SCALE = 0.6f

/**
 * チェックを見せておく時間。shortcut: M3 のトークンに「見せておく時間」は無いため固定（Gerbil の文字送りと同じ例外）。
 * 読める長さで、次の操作を待たせない程度。
 */
private const val SAVED_CHECK_HOLD_MS = 1_200L
