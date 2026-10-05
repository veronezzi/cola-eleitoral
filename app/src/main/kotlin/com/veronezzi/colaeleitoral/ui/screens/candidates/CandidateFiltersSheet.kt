package com.veronezzi.colaeleitoral.ui.screens.candidates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.SortOrder
import com.veronezzi.colaeleitoral.ui.components.SectionHeader

/**
 * Filters and order (ARCHITECTURE.md 4.4): office of the ballot, parties and verbatim status texts
 * present in the list (multi-select), "Só 2º turno" in the second round, and the order (number by
 * default, or alphabetical by name or party). Predictive back and drag dismiss the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CandidateFiltersSheet(
    state: CandidateListUiState,
    actions: CandidateListActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CandidateFiltersContent(state = state, actions = actions, onDismiss = onDismiss)
    }
}

/** Content of [CandidateFiltersSheet], stateless (the store screenshots draw it outside a sheet). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CandidateFiltersContent(
    state: CandidateListUiState,
    actions: CandidateListActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.list_filters_and_order),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val offices = state.offices.distinctBy { it.code }
        if (offices.size > 1) {
            SectionHeader(text = stringResource(R.string.filters_office))
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                offices.forEach { office ->
                    SelectChip(
                        label = office.name,
                        selected = office.code == state.officeCode,
                        onClick = {
                            if (office.code != state.officeCode) {
                                onDismiss()
                                actions.onOfficeSelected(office)
                            }
                        },
                    )
                }
            }
        }

        if (state.isSecondRound) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = state.filter.onlySecondRound,
                        role = Role.Switch,
                        onValueChange = actions.onOnlySecondRoundChange,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filters_only_second_round),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.filter.onlySecondRound, onCheckedChange = null)
            }
        }

        if (state.options.parties.isNotEmpty()) {
            SectionHeader(text = stringResource(R.string.filters_party))
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.options.parties.distinctBy { it.acronym }.forEach { party ->
                    SelectChip(
                        label = party.chipLabel(),
                        selected = party.acronym in state.filter.partyAcronyms,
                        onClick = { actions.onPartyToggled(party.acronym) },
                    )
                }
            }
        }

        if (state.options.registrationStatuses.isNotEmpty()) {
            SectionHeader(text = stringResource(R.string.filters_status))
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.options.registrationStatuses.forEach { status ->
                    SelectChip(
                        label = status.ifBlank { stringResource(R.string.status_not_informed) },
                        selected = status in state.filter.registrationStatuses,
                        onClick = { actions.onStatusToggled(status) },
                    )
                }
            }
        }

        SectionHeader(text = stringResource(R.string.filters_order))
        val orders = SortOrder.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            orders.forEachIndexed { index, order ->
                SegmentedButton(
                    selected = state.filter.sortOrder == order,
                    onClick = { actions.onSortChange(order) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size),
                ) {
                    Text(stringResource(sortLabel(order)), maxLines = 2)
                }
            }
        }
        Text(
            text = stringResource(R.string.filters_neutrality_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions.onClearFilters, enabled = state.activeFilterCount > 0) {
                Text(stringResource(R.string.list_clear_filters))
            }
            Button(onClick = onDismiss) {
                Text(pluralStringResource(R.plurals.filters_show_results, state.items.size, state.items.size))
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}
