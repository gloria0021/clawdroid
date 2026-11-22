package jp.miyavi.android.clawdroid.feature.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import coil3.compose.AsyncImage
import jp.miyavi.android.clawdroid.R
import jp.miyavi.android.clawdroid.core.data.Agent
import jp.miyavi.android.clawdroid.core.designsystem.Dimens
import jp.miyavi.android.clawdroid.core.designsystem.Spacing
import jp.miyavi.android.clawdroid.feature.chat.message.webUri

@Composable
internal fun AgentAvatar(agent: Agent?, label: String? = null, size: Dp = Dimens.avatar) {
    val url = remember(agent?.avatarUrl) {
        agent?.avatarUrl?.trim()?.takeIf { it.startsWith("data:image/", ignoreCase = true) || webUri(it) != null }
    }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape).clearAndSetSemantics {
            if (label != null) contentDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val glyph = agent?.emoji?.takeIf(String::isNotBlank)
                ?: agent?.name?.firstOrNull()?.takeUnless { it.isSurrogate() }?.toString()
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (glyph != null) {
                    Text(glyph, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                } else {
                    Icon(painterResource(R.drawable.ic_huge_launcher_glyph), null, Modifier.size(Spacing.l))
                }
            }
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
