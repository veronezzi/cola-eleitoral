package com.veronezzi.meusantinho.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.veronezzi.meusantinho.R
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.DataSource
import com.veronezzi.meusantinho.ui.common.Freshness
import com.veronezzi.meusantinho.ui.common.RelativeAge
import com.veronezzi.meusantinho.ui.common.formatDayMonthTime
import com.veronezzi.meusantinho.ui.common.relativeAge
import com.veronezzi.meusantinho.ui.common.staleReason
import com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme
import java.time.Duration
import java.time.Instant

/**
 * Data-source indicator, always next to TSE data (ARCHITECTURE.md 1.3 and 2.8): which TSE system
 * served it, when it was downloaded ("atualizado há 5 minutos"), and, when the cache is stale or
 * offline, a notice with the reason and a retry action. Status changes are announced politely.
 */
@Composable
fun DataFreshnessBar(
    freshness: Freshness,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val now = rememberNowOnResume()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(sourceLabel(freshness.source)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = updatedText(freshness.fetchedAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (freshness.isStale && freshness.hasData) {
            StaleNotice(freshness = freshness, onRetry = onRetry)
        }
    }
}

@Composable
private fun StaleNotice(freshness: Freshness, onRetry: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (freshness.isOffline) Icons.Outlined.CloudOff else Icons.Outlined.Update,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.freshness_stale_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                freshness.lastError?.let { error ->
                    Text(
                        text = stringResource(error.staleReason()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

private fun sourceLabel(source: DataSource): Int = when (source) {
    DataSource.DIVULGA_CAND_CONTAS -> R.string.freshness_source_divulga
    DataSource.TSE_OPEN_DATA -> R.string.freshness_source_open_data
}

@Composable
private fun updatedText(fetchedAt: Instant?, now: Instant): String {
    if (fetchedAt == null) return stringResource(R.string.freshness_never)
    val ago = when (val age = relativeAge(fetchedAt, now)) {
        RelativeAge.JustNow -> stringResource(R.string.freshness_just_now)
        is RelativeAge.Minutes -> pluralStringResource(R.plurals.freshness_minutes_ago, age.count, age.count)
        is RelativeAge.Hours -> pluralStringResource(R.plurals.freshness_hours_ago, age.count, age.count)
        is RelativeAge.Days -> pluralStringResource(R.plurals.freshness_days_ago, age.count, age.count)
    }
    return stringResource(R.string.freshness_updated, ago, fetchedAt.formatDayMonthTime())
}

/**
 * The current instant, refreshed whenever the screen resumes. Relative times ("há 5 minutos")
 * update when the user comes back, without a ticking coroutine.
 */
@Composable
fun rememberNowOnResume(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LifecycleResumeEffect(Unit) {
        now = Instant.now()
        onPauseOrDispose { }
    }
    return now
}

@ThemePreviews
@Composable
private fun DataFreshnessBarPreview() {
    MeuSantinhoTheme(dynamicColor = false) {
        PreviewSurface {
            Column {
                DataFreshnessBar(
                    freshness = Freshness(
                        fetchedAt = Instant.now().minus(Duration.ofMinutes(5)),
                        isStale = false,
                        lastError = null,
                        source = DataSource.DIVULGA_CAND_CONTAS,
                    ),
                )
                DataFreshnessBar(
                    freshness = Freshness(
                        fetchedAt = Instant.now().minus(Duration.ofHours(3)),
                        isStale = true,
                        lastError = AppError.Network,
                        source = DataSource.TSE_OPEN_DATA,
                    ),
                    onRetry = {},
                )
            }
        }
    }
}
