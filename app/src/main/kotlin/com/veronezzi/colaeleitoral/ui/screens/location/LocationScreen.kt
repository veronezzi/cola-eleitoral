package com.veronezzi.colaeleitoral.ui.screens.location

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.FederativeUnits
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.common.presentation
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.ErrorState
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.MessageState
import com.veronezzi.colaeleitoral.ui.components.NoPersonalizedLearning
import com.veronezzi.colaeleitoral.ui.components.PrivateSearchKeyboardOptions
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.navigation.LocationRoute
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

@Composable
fun LocationRouteScreen(
    route: LocationRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: LocationViewModel = hiltViewModel<LocationViewModel, LocationViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    BackHandler(enabled = state.step == LocationStep.MUNICIPALITY) { viewModel.onBackToUf() }
    LocationScreen(
        state = state,
        query = viewModel.query,
        onBack = if (state.step == LocationStep.MUNICIPALITY) viewModel::onBackToUf else onBack.takeIf { route.fromSettings },
        onUfSelected = viewModel::onUfSelected,
        onQueryChange = viewModel::onQueryChange,
        onMunicipalitySelected = viewModel::onMunicipalitySelected,
        onSkipMunicipality = viewModel::onSkipMunicipality,
        onRetry = viewModel::onRetry,
        onSaveErrorShown = viewModel::onSaveErrorShown,
    )
}

/** @param query text of the municipality search, read from the ViewModel's Compose state. */
@Composable
fun LocationScreen(
    state: LocationUiState,
    query: String,
    onBack: (() -> Unit)?,
    onUfSelected: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onMunicipalitySelected: (ElectoralUnit) -> Unit,
    onSkipMunicipality: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveErrorShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.saveError?.let { error ->
        val text = stringResource(error.presentation().message)
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(text, withDismissAction = true)
            onSaveErrorShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.location_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                when (state.step) {
                    LocationStep.UF -> UfList(state = state, onUfSelected = onUfSelected)
                    LocationStep.MUNICIPALITY -> MunicipalityStep(
                        state = state,
                        query = query,
                        onQueryChange = onQueryChange,
                        onMunicipalitySelected = onMunicipalitySelected,
                        onSkip = onSkipMunicipality,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun UfList(state: LocationUiState, onUfSelected: (String) -> Unit) {
    LazyColumn(modifier = Modifier.selectableGroup()) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.location_uf_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(FederativeUnits.all, key = { it.code }) { unit ->
            ChoiceRow(
                title = unit.name,
                supporting = unit.code,
                selected = state.currentLocation?.uf == unit.code,
                enabled = !state.isSaving,
                onClick = { onUfSelected(unit.code) },
            )
        }
        item(key = ElectoralUnit.ABROAD_CODE) {
            ChoiceRow(
                title = stringResource(R.string.location_abroad),
                supporting = stringResource(R.string.location_abroad_supporting),
                selected = state.currentLocation?.isAbroad == true,
                enabled = !state.isSaving,
                onClick = { onUfSelected(ElectoralUnit.ABROAD_CODE) },
            )
        }
    }
}

@Composable
private fun MunicipalityStep(
    state: LocationUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onMunicipalitySelected: (ElectoralUnit) -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
) {
    val ufName = state.selectedUf?.let { FederativeUnits.byCode(it)?.name } ?: state.selectedUf.orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(
                    if (state.municipalityRequired) R.string.location_municipality_required else R.string.location_municipality_optional,
                    ufName,
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            NoPersonalizedLearning {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.location_search_label)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = PrivateSearchKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!state.municipalityRequired) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.location_skip_municipality))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (val load = state.municipalitiesLoad) {
                LoadState.Loading -> ListSkeleton(description = stringResource(R.string.location_loading), rows = 6, avatarSize = 24.dp)
                is LoadState.Failed -> ErrorState(error = load.error, onRetry = onRetry)
                LoadState.Loaded -> if (state.municipalities.isEmpty()) {
                    MessageState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.location_no_match_title),
                        message = stringResource(R.string.location_no_match_message),
                    )
                } else {
                    LazyColumn(modifier = Modifier.selectableGroup()) {
                        items(state.municipalities, key = { it.code }) { municipality ->
                            ChoiceRow(
                                title = municipality.name,
                                supporting = null,
                                selected = state.currentLocation?.municipality?.code == municipality.code,
                                enabled = !state.isSaving,
                                onClick = { onMunicipalitySelected(municipality) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    supporting: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.location_current)) }
        } else {
            null
        },
        modifier = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    )
    HorizontalDivider()
}

@ScreenPreviews
@Composable
private fun LocationScreenPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        LocationScreen(
            state = LocationUiState(
                step = LocationStep.MUNICIPALITY,
                selectedUf = "AC",
                municipalities = listOf(
                    ElectoralUnit("01120", "ACRELÂNDIA", "AC", isMunicipality = true),
                    ElectoralUnit("01570", "BRASILÉIA", "AC", isMunicipality = true),
                ),
                municipalitiesLoad = LoadState.Loaded,
            ),
            query = "",
            onBack = {},
            onUfSelected = {},
            onQueryChange = {},
            onMunicipalitySelected = {},
            onSkipMunicipality = {},
            onRetry = {},
        )
    }
}
