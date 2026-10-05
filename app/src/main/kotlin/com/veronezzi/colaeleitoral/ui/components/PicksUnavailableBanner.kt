package com.veronezzi.colaeleitoral.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdCardAlert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/**
 * Shown while the saved picks exist but cannot be read right now (a transient Keystore or I/O
 * failure, typically just after boot). It is not an error screen: nothing was deleted, the rest of
 * the screen keeps working and [onRetry] reads the picks again (the repository also retries on
 * its own). Announced politely by TalkBack when it appears.
 */
@Composable
fun PicksUnavailableBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(PICKS_UNAVAILABLE_TAG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.SdCardAlert, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.picks_unavailable_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.picks_unavailable_message), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

const val PICKS_UNAVAILABLE_TAG = "picks_unavailable"

@ThemePreviews
@Composable
private fun PicksUnavailableBannerPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        PreviewSurface { PicksUnavailableBanner(onRetry = {}) }
    }
}
