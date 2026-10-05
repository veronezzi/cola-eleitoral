package com.veronezzi.colaeleitoral.ui.screens.candidates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.FilterOptions
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.Party
import com.veronezzi.colaeleitoral.domain.model.SortOrder
import com.veronezzi.colaeleitoral.ui.common.SecureScreen
import com.veronezzi.colaeleitoral.ui.common.slotLabel
import com.veronezzi.colaeleitoral.ui.common.spokenDigits
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.CandidateAvatar
import com.veronezzi.colaeleitoral.ui.components.DataFreshnessBar
import com.veronezzi.colaeleitoral.ui.components.ErrorState
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.MessageState
import com.veronezzi.colaeleitoral.ui.components.NoPersonalizedLearning
import com.veronezzi.colaeleitoral.ui.components.PrivateSearchKeyboardOptions
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.StatusChip
import com.veronezzi.colaeleitoral.ui.preview.PreviewData
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import com.veronezzi.colaeleitoral.ui.theme.NumberTextStyle
import java.text.NumberFormat
import java.util.Locale

/** User actions of the candidate list; the stateful wrapper binds them to the ViewModel. */
data class CandidateListActions(
    val onBack: () -> Unit,
    val onCandidateClick: (Long) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onPartyToggled: (String) -> Unit,
    val onStatusToggled: (String) -> Unit,
    val onOnlySecondRoundChange: (Boolean) -> Unit,
    val onSortChange: (SortOrder) -> Unit,
    val onClearFilters: () -> Unit,
    val onOfficeSelected: (Office) -> Unit,
    val onRefresh: () -> Unit,
)

/**
 * @param query text of the search field, read straight from the ViewModel's Compose state (not
 * from [state]), so typing fast never loses characters or moves the cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateListScreen(
    state: CandidateListUiState,
    query: String,
    actions: CandidateListActions,
    modifier: Modifier = Modifier,
    selectedCandidateId: Long? = null,
) {
    // The user's pick is marked in the list: protect the window while it is visible.
    SecureScreen(active = state.items.any { it.isSaved || it.isInOtherSlot })
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val title = slotLabel(state.officeName, state.slot, state.hasSeveralSeats)
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = title,
                onBack = actions.onBack,
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(
                            badge = {
                                if (state.activeFilterCount > 0) Badge { Text(state.activeFilterCount.toString()) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = if (state.activeFilterCount > 0) {
                                    pluralStringResource(
                                        R.plurals.list_filters_active,
                                        state.activeFilterCount,
                                        state.activeFilterCount,
                                    )
                                } else {
                                    stringResource(R.string.list_filters)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.content != ListContent.Loading,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag(LIST_TEST_TAG)) {
                item(key = "search") {
                    SearchField(query = query, onQueryChange = actions.onQueryChange)
                }
                item(key = "summary") {
                    ListSummary(state = state, onOpenFilters = { showFilters = true })
                }
                when (val content = state.content) {
                    ListContent.Loading -> item(key = "loading") {
                        ListSkeleton(description = stringResource(R.string.list_loading))
                    }
                    is ListContent.Failed -> item(key = "error") {
                        ErrorState(error = content.error, onRetry = actions.onRefresh)
                    }
                    ListContent.EmptyFromTse -> item(key = "empty-tse") {
                        MessageState(
                            icon = Icons.Outlined.PersonSearch,
                            title = stringResource(R.string.list_empty_tse_title),
                            message = stringResource(R.string.list_empty_tse_message),
                        )
                    }
                    ListContent.EmptyForFilters -> item(key = "empty-filters") {
                        MessageState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.list_empty_filters_title),
                            message = stringResource(
                                if (state.filter.onlySecondRound) {
                                    R.string.list_empty_runoff_message
                                } else {
                                    R.string.list_empty_filters_message
                                },
                            ),
                        ) {
                            if (state.activeFilterCount > 0) {
                                OutlinedButton(onClick = actions.onClearFilters) {
                                    Text(stringResource(R.string.list_clear_filters))
                                }
                            }
                        }
                    }
                    ListContent.Items -> items(state.items, key = { it.candidate.id }) { item ->
                        CandidateRow(
                            item = item,
                            showPhoto = state.showPhotos,
                            selected = item.candidate.id == selectedCandidateId,
                            onClick = { actions.onCandidateClick(item.candidate.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                    }
                }
                state.freshness?.takeIf { state.content != ListContent.Loading }?.let { freshness ->
                    item(key = "freshness") { DataFreshnessBar(freshness = freshness, onRetry = actions.onRefresh) }
                }
            }
        }
    }
    if (showFilters) {
        CandidateFiltersSheet(
            state = state,
            actions = actions,
            onDismiss = { showFilters = false },
        )
    }
}

/** Search by name, number or party. The keyboard does not learn what is typed here (S13). */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    NoPersonalizedLearning {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.list_search_label)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.list_search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = PrivateSearchKeyboardOptions,
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SEARCH_TEST_TAG),
        )
    }
}

@Composable
private fun ListSummary(state: CandidateListUiState, onOpenFilters: () -> Unit) {
    val format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.unitName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.content == ListContent.Items || state.content == ListContent.EmptyForFilters) {
            Text(
                text = pluralStringResource(
                    R.plurals.list_showing,
                    state.totalCount,
                    format.format(state.items.size),
                    format.format(state.totalCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.list_sorted_by, stringResource(sortLabel(state.filter.sortOrder))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onOpenFilters) {
                Text(stringResource(R.string.list_filters_and_order))
            }
        }
    }
}

fun sortLabel(order: SortOrder): Int = when (order) {
    SortOrder.NUMBER -> R.string.sort_by_number
    SortOrder.BALLOT_NAME -> R.string.sort_by_name
    SortOrder.PARTY -> R.string.sort_by_party
}

/**
 * One candidacy. Every row has the same visual weight (ARCHITECTURE.md 4.4): photo or initials,
 * the number in evidence (read digit by digit), ballot name, party, coalition and the verbatim
 * status in a neutral chip. The user's own pick carries an icon and a text, never color alone.
 */
@Composable
private fun CandidateRow(
    item: CandidateItem,
    showPhoto: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val candidate = item.candidate
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.list_open_detail), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CandidateAvatar(
            name = candidate.ballotName,
            photoUrl = candidate.photoUrl.takeIf { showPhoto },
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val spokenNumber = stringResource(R.string.candidate_number_spoken, spokenDigits(item.numberText))
            Text(
                text = item.numberText,
                style = MaterialTheme.typography.headlineSmall.merge(NumberTextStyle),
                modifier = Modifier.semantics { contentDescription = spokenNumber },
            )
            Text(text = candidate.ballotName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = candidate.party.acronym,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            candidate.coalition?.takeIf { it.isNotBlank() && it != candidate.party.acronym }?.let { coalition ->
                Text(
                    text = coalition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(text = candidate.status.registration)
            }
            if (item.isSaved || item.isInOtherSlot) {
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (item.isSaved) R.string.list_saved_here else R.string.list_saved_other_slot,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

const val SEARCH_TEST_TAG = "candidate_search"
const val LIST_TEST_TAG = "candidate_list"

@ScreenPreviews
@Composable
private fun CandidateListScreenPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        CandidateListScreen(
            state = CandidateListUiState(
                officeCode = PreviewData.senator.code,
                officeName = PreviewData.senator.name,
                unitName = "São Paulo",
                slot = 1,
                maxPicks = 2,
                isSecondRound = false,
                options = FilterOptions(
                    parties = PreviewData.candidates.map { it.party }.distinct(),
                    registrationStatuses = listOf("Deferido", "Indeferido"),
                ),
                items = PreviewData.candidates.mapIndexed { index, candidate ->
                    CandidateItem(candidate, candidate.number.toString(), isSaved = index == 0, isInOtherSlot = false)
                },
                totalCount = PreviewData.candidates.size,
                content = ListContent.Items,
                freshness = PreviewData.freshness(),
            ),
            query = "",
            actions = previewListActions,
        )
    }
}

@ScreenPreviews
@Composable
private fun CandidateListLoadingPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        CandidateListScreen(
            state = CandidateListUiState(
                officeCode = 6,
                officeName = "Deputado Federal",
                unitName = "São Paulo",
                slot = 1,
                maxPicks = 1,
                isSecondRound = false,
            ),
            query = "",
            actions = previewListActions,
        )
    }
}

private val previewListActions = CandidateListActions(
    onBack = {},
    onCandidateClick = {},
    onQueryChange = {},
    onPartyToggled = {},
    onStatusToggled = {},
    onOnlySecondRoundChange = {},
    onSortChange = {},
    onClearFilters = {},
    onOfficeSelected = {},
    onRefresh = {},
)

/** Party label for filter chips: the acronym the TSE publishes. */
internal fun Party.chipLabel(): String = acronym
