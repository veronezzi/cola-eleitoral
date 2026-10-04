package com.veronezzi.meusantinho.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.veronezzi.meusantinho.ui.StartDestination
import com.veronezzi.meusantinho.ui.screens.about.AboutActions
import com.veronezzi.meusantinho.ui.screens.about.AboutRouteScreen
import com.veronezzi.meusantinho.ui.screens.about.LicensesScreen
import com.veronezzi.meusantinho.ui.screens.about.PrivacyPolicyScreen
import com.veronezzi.meusantinho.ui.screens.ballot.BallotActions
import com.veronezzi.meusantinho.ui.screens.ballot.BallotRouteScreen
import com.veronezzi.meusantinho.ui.screens.candidates.CandidatesListDetail
import com.veronezzi.meusantinho.ui.screens.cola.ColaExportRouteScreen
import com.veronezzi.meusantinho.ui.screens.detail.CandidateDetailRouteScreen
import com.veronezzi.meusantinho.ui.screens.home.HomeActions
import com.veronezzi.meusantinho.ui.screens.home.HomeRoute as HomeScreenRoute
import com.veronezzi.meusantinho.ui.screens.location.LocationRouteScreen
import com.veronezzi.meusantinho.ui.screens.onboarding.OnboardingNext
import com.veronezzi.meusantinho.ui.screens.onboarding.OnboardingRoute as OnboardingScreenRoute
import com.veronezzi.meusantinho.ui.screens.settings.SettingsRouteScreen

/**
 * Navigation graph: OnboardingRoute -> LocationRoute -> HomeRoute (clearing the back stack), the
 * three first-level destinations in a bottom bar or rail, and the list-detail of candidates.
 * Predictive back works through the NavHost, the list-detail scaffold and the sheets.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: StartDestination,
    ballotTarget: BallotRoute?,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val destination = currentEntry?.destination
    val showNavigation = destination != null &&
        !destination.hasRoute(OnboardingRoute::class) &&
        !destination.hasRoute(LocationRoute::class)
    // Sub-screens (list, detail, cola, settings...) keep the tab they were opened from selected.
    var lastTab by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    val currentTab = destination?.asTopLevel()
    LaunchedEffect(currentTab) { currentTab?.let { lastTab = it } }
    val selectedTab = currentTab ?: lastTab
    val start: Any = remember(startDestination) {
        when (startDestination) {
            StartDestination.ONBOARDING -> OnboardingRoute
            StartDestination.LOCATION -> LocationRoute()
            StartDestination.HOME -> HomeRoute
        }
    }

    AppNavigationScaffold(
        showNavigation = showNavigation,
        selected = selectedTab,
        ballotEnabled = ballotTarget != null,
        onSelect = { tab ->
            val route: Any? = when (tab) {
                TopLevelDestination.HOME -> HomeRoute
                TopLevelDestination.BALLOT -> ballotTarget
                TopLevelDestination.ABOUT -> AboutRoute
            }
            route?.let { navController.navigate(it) { topLevelOptions() } }
        },
    ) {
        NavHost(navController = navController, startDestination = start) {
            composable<OnboardingRoute> {
                OnboardingScreenRoute(
                    onContinue = { next ->
                        val target: Any = if (next == OnboardingNext.HOME) HomeRoute else LocationRoute()
                        navController.navigate(target) { popUpTo<OnboardingRoute> { inclusive = true } }
                    },
                )
            }
            composable<LocationRoute> { entry ->
                val route = entry.toRoute<LocationRoute>()
                LocationRouteScreen(
                    route = route,
                    onDone = {
                        if (route.fromSettings) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HomeRoute> {
                HomeScreenRoute(
                    actions = HomeActions(
                        onOpenSlot = { electionId, year, slot, round ->
                            navController.navigate(
                                CandidateListRoute(electionId, year, slot.office.ueCode, slot.office.code, round.number, slot.slot),
                            )
                        },
                        onOpenBallot = { electionId, round ->
                            navController.navigate(BallotRoute(electionId, round.number)) { topLevelOptions() }
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onChangeLocation = { navController.navigate(LocationRoute(fromSettings = true)) },
                    ),
                )
            }
            composable<CandidateListRoute> { entry ->
                val route = entry.toRoute<CandidateListRoute>()
                CandidatesListDetail(
                    route = route,
                    onBack = { navController.popBackStack() },
                    onOfficeSelected = { office ->
                        navController.navigate(route.copy(ueCode = office.ueCode, officeCode = office.code, slot = 1)) {
                            popUpTo<CandidateListRoute> { inclusive = true }
                        }
                    },
                    onOpenBallot = {
                        navController.navigate(BallotRoute(route.electionId, route.round)) { topLevelOptions() }
                    },
                )
            }
            composable<CandidateDetailRoute> { entry ->
                val route = entry.toRoute<CandidateDetailRoute>()
                CandidateDetailRouteScreen(
                    route = route,
                    onBack = { navController.popBackStack() },
                    onOpenBallot = {
                        navController.navigate(BallotRoute(route.electionId, route.round)) { topLevelOptions() }
                    },
                )
            }
            composable<BallotRoute> { entry ->
                BallotRouteScreen(
                    route = entry.toRoute<BallotRoute>(),
                    actions = BallotActions(
                        onChoose = { slot, electionId, year, round ->
                            navController.navigate(
                                CandidateListRoute(electionId, year, slot.office.ueCode, slot.office.code, round.number, slot.slot),
                            )
                        },
                        onOpenPick = { pick ->
                            navController.navigate(
                                CandidateDetailRoute(
                                    electionId = pick.electionId,
                                    year = pick.electionYear,
                                    ueCode = pick.ueCode,
                                    officeCode = pick.officeCode,
                                    candidateId = pick.candidateId,
                                    round = pick.round.number,
                                    slot = pick.slot,
                                ),
                            )
                        },
                        onExport = { electionId, round -> navController.navigate(ColaExportRoute(electionId, round.number)) },
                    ),
                )
            }
            composable<ColaExportRoute> { entry ->
                ColaExportRouteScreen(route = entry.toRoute<ColaExportRoute>(), onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsRouteScreen(
                    onBack = { navController.popBackStack() },
                    onChangeLocation = { navController.navigate(LocationRoute(fromSettings = true)) },
                    onDataDeleted = {
                        navController.navigate(OnboardingRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                    },
                )
            }
            composable<AboutRoute> {
                AboutRouteScreen(
                    actions = AboutActions(
                        onOpenPrivacy = { navController.navigate(PrivacyPolicyRoute) },
                        onOpenLicenses = { navController.navigate(LicensesRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    ),
                )
            }
            composable<PrivacyPolicyRoute> { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
            composable<LicensesRoute> { LicensesScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

/** First-level navigation: one copy of each tab, on top of Home. */
private fun NavOptionsBuilder.topLevelOptions() {
    popUpTo<HomeRoute>()
    launchSingleTop = true
}

private fun NavDestination.asTopLevel(): TopLevelDestination? = when {
    hasRoute(HomeRoute::class) -> TopLevelDestination.HOME
    hasRoute(BallotRoute::class) -> TopLevelDestination.BALLOT
    hasRoute(AboutRoute::class) -> TopLevelDestination.ABOUT
    else -> null
}
