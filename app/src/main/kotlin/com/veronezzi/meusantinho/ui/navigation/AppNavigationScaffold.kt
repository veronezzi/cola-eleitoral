package com.veronezzi.meusantinho.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass
import com.veronezzi.meusantinho.R

/** First-level destinations (ARCHITECTURE.md 4.1): Início, Meu santinho, Sobre. */
enum class TopLevelDestination(
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BALLOT(R.string.nav_ballot, Icons.Filled.HowToVote, Icons.Outlined.HowToVote),
    ABOUT(R.string.nav_about, Icons.Filled.Info, Icons.Outlined.Info),
}

/**
 * Bottom navigation bar on compact widths, navigation rail from medium widths on (material3
 * adaptive window size classes). The bar or rail consumes its own system-bar insets so the
 * screens' Scaffolds do not pad twice.
 */
@Composable
fun AppNavigationScaffold(
    showNavigation: Boolean,
    selected: TopLevelDestination?,
    ballotEnabled: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!showNavigation) {
        content()
        return
    }
    val useRail = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        enabled = destination != TopLevelDestination.BALLOT || ballotEnabled,
                        icon = { DestinationIcon(destination, destination == selected) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
            ) {
                content()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) {
                content()
            }
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        enabled = destination != TopLevelDestination.BALLOT || ballotEnabled,
                        icon = { DestinationIcon(destination, destination == selected) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean) {
    // The label already names the destination; the icon is decorative.
    Icon(imageVector = if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
}
