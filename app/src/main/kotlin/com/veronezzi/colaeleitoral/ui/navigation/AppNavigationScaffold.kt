package com.veronezzi.colaeleitoral.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.veronezzi.colaeleitoral.R

/** First-level destinations (ARCHITECTURE.md 4.1): Início, Minha cola, Sobre. */
enum class TopLevelDestination(
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BALLOT(R.string.nav_ballot, Icons.Filled.HowToVote, Icons.Outlined.HowToVote),
    ABOUT(R.string.nav_about, Icons.Filled.Info, Icons.Outlined.Info),
}

/** Which navigation chrome surrounds the content. */
enum class NavigationChrome { NONE, BAR, RAIL }

/**
 * Bottom navigation bar on compact widths, navigation rail from medium widths on (material3
 * adaptive window size classes), none on the first-run screens. The bar or rail consumes its own
 * system-bar insets so the screens' Scaffolds do not pad twice.
 *
 * The tree is fixed: [content] (the NavHost) always sits at the same place and only the bar or
 * rail comes and goes. Entering or leaving the first-run screens, or resizing the window, never
 * recreates the NavHost, so its saved state (scroll, `rememberSaveable` of the back stack) and the
 * transition animations survive.
 */
@Composable
fun AppNavigationScaffold(
    showNavigation: Boolean,
    selected: TopLevelDestination?,
    ballotEnabled: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val useRail = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val chrome = when {
        !showNavigation -> NavigationChrome.NONE
        useRail -> NavigationChrome.RAIL
        else -> NavigationChrome.BAR
    }
    AppNavigationLayout(chrome = chrome, selected = selected, ballotEnabled = ballotEnabled, onSelect = onSelect, content = content)
}

/** [AppNavigationScaffold] with the chrome decided by the caller (tests use it directly). */
@Composable
fun AppNavigationLayout(
    chrome: NavigationChrome,
    selected: TopLevelDestination?,
    ballotEnabled: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (chrome == NavigationChrome.RAIL) {
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
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (chrome == NavigationChrome.RAIL) {
                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (chrome == NavigationChrome.BAR) {
                            Modifier.consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
            }
            if (chrome == NavigationChrome.BAR) {
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
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean) {
    // The label already names the destination; the icon is decorative.
    Icon(imageVector = if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
}
