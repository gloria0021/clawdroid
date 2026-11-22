package jp.miyavi.android.clawdroid.feature.chat.message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.MessagePart
import jp.miyavi.android.clawdroid.core.designsystem.IMAGE_OPEN_START_SCALE
import jp.miyavi.android.clawdroid.core.designsystem.Spacing

/**
 * 画像の全画面オーバーレイ（AGENTS.md 2.7）。背景はフェード、画像は拡大で出す。
 * 消える動きを終えてから閉じる（MutableTransitionState、AGENTS.md 2.8）。
 */
@Composable
fun ImageOverlay(image: MessagePart.Image?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf<MessagePart.Image?>(null) }
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(image) {
        if (image != null) {
            shown = image
            visible.targetState = true
        } else {
            visible.targetState = false
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && shown != null) shown = null
    }
    val current = shown ?: return
    BackHandler(enabled = visible.targetState) { onDismiss() }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val cd = stringResource(R.string.chat_image_cd)
            val imageModifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .animateEnterExit(
                    enter = scaleIn(motion.defaultSpatialSpec(), initialScale = IMAGE_OPEN_START_SCALE),
                    exit = scaleOut(motion.fastSpatialSpec(), targetScale = IMAGE_OPEN_START_SCALE),
                )
            val bitmap = rememberBase64Bitmap(current.base64)
            when {
                bitmap != null -> Image(bitmap, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
                current.url != null -> AsyncImage(model = current.url, contentDescription = cd, contentScale = ContentScale.Fit, modifier = imageModifier)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.chat_image_close_cd), tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}
