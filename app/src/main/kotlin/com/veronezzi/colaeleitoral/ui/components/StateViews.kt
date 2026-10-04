package com.veronezzi.colaeleitoral.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.ui.common.TseLinks
import com.veronezzi.colaeleitoral.ui.common.openInBrowser
import com.veronezzi.colaeleitoral.ui.common.presentation
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/**
 * Full error state for screens without cached data, one per [AppError] type (ARCHITECTURE.md
 * 2.6): `Blocked` explains the refusal and links to the official site; `Network` offers a retry.
 */
@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    hasCache: Boolean = false,
    aboutCandidate: Boolean = false,
    officialUrl: String = TseLinks.DIVULGA_HOME,
) {
    val presentation = error.presentation(hasCache = hasCache, aboutCandidate = aboutCandidate)
    val context = LocalContext.current
    MessageState(
        icon = error.icon(),
        title = stringResource(presentation.title),
        message = stringResource(presentation.message),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (presentation.showOfficialSite) {
            Button(onClick = { context.openInBrowser(TseLinks.officialOrHome(officialUrl)) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_open_tse_site))
            }
        }
        if (presentation.canRetry && onRetry != null) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

private fun AppError.icon(): ImageVector = when (this) {
    AppError.Network -> Icons.Outlined.CloudOff
    is AppError.Blocked -> Icons.Outlined.Block
    AppError.NotFound -> Icons.Outlined.SearchOff
    else -> Icons.Outlined.ErrorOutline
}

/** Empty state: an icon, a title, an explanation and optional actions. */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions()
        }
    }
}

/** Placeholder block with a slow pulse. Infinite animations do not keep Compose tests busy. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/**
 * Skeleton of a list (candidates, offices, municipalities) while the first download runs. Read by
 * TalkBack as a single "loading" node with [description].
 */
@Composable
fun ListSkeleton(
    description: String,
    modifier: Modifier = Modifier,
    rows: Int = 6,
    avatarSize: Dp = 48.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(modifier = Modifier.size(avatarSize), shape = CircleShape)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.35f).height(20.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ErrorStatePreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        PreviewSurface {
            ErrorState(error = AppError.Blocked(403), onRetry = {})
        }
    }
}

@ThemePreviews
@Composable
private fun ListSkeletonPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        PreviewSurface {
            ListSkeleton(description = "", rows = 3)
        }
    }
}
