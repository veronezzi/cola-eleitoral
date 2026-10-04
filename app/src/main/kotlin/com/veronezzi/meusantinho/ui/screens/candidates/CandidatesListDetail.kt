package com.veronezzi.meusantinho.ui.screens.candidates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.meusantinho.R
import com.veronezzi.meusantinho.domain.model.Office
import com.veronezzi.meusantinho.ui.components.MessageState
import com.veronezzi.meusantinho.ui.navigation.CandidateListRoute
import com.veronezzi.meusantinho.ui.navigation.detail
import com.veronezzi.meusantinho.ui.screens.detail.CandidateDetailRouteScreen
import kotlinx.coroutines.launch

/**
 * Candidate list and detail (ARCHITECTURE.md 4.1). On compact widths one pane at a time (the
 * detail replaces the list, with predictive back); on medium and expanded widths both side by
 * side. The detail content key is the candidate id, so the selection survives rotation and
 * process death; [CandidateListRoute] supplies the rest of the detail route.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CandidatesListDetail(
    route: CandidateListRoute,
    onBack: () -> Unit,
    onOfficeSelected: (Office) -> Unit,
    onOpenBallot: () -> Unit,
    viewModel: CandidateListViewModel = hiltViewModel<CandidateListViewModel, CandidateListViewModel.Factory> {
        it.create(route)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    val detailOnly = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val selectedId = navigator.currentDestination
        ?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }
        ?.contentKey
    val actions = remember(viewModel, navigator, onBack, onOfficeSelected) {
        CandidateListActions(
            onBack = onBack,
            onCandidateClick = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
            onQueryChange = viewModel::onQueryChange,
            onPartyToggled = viewModel::onPartyToggled,
            onStatusToggled = viewModel::onStatusToggled,
            onOnlySecondRoundChange = viewModel::onOnlySecondRoundChange,
            onSortChange = viewModel::onSortChange,
            onClearFilters = viewModel::onClearFilters,
            onOfficeSelected = onOfficeSelected,
            onRefresh = viewModel::onRefresh,
        )
    }
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                CandidateListScreen(
                    state = state,
                    actions = actions,
                    selectedCandidateId = selectedId.takeUnless { detailOnly },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (selectedId != null) {
                    CandidateDetailRouteScreen(
                        route = route.detail(selectedId),
                        onBack = if (detailOnly) {
                            { scope.launch { navigator.navigateBack() } }
                        } else {
                            null
                        },
                        onOpenBallot = onOpenBallot,
                    )
                } else {
                    Surface {
                        MessageState(
                            icon = Icons.Outlined.PersonSearch,
                            title = stringResource(R.string.list_detail_placeholder_title),
                            message = stringResource(R.string.list_detail_placeholder_message),
                        )
                    }
                }
            }
        },
    )
}
