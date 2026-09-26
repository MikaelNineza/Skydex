package com.skydex.app.ui.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.ListDivider
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView
import com.skydex.app.ui.common.PixelIcon
import com.skydex.app.ui.common.SectionHeader
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.ui.common.UiState
import com.skydex.app.ui.common.cropIcon
import com.skydex.app.ui.common.cropsLabel
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventType
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Perk

@Composable
fun EventsScreen(modifier: Modifier = Modifier, viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        EventsUiState.Loading -> LoadingView(modifier)
        EventsUiState.Unavailable -> MessageView(
            title = "Calendar unavailable",
            body = "Event times can't be computed in this version of the app.",
            modifier = modifier,
        )
        is EventsUiState.Content -> {
            EventList(s, viewModel::select, viewModel::retryLive, modifier)
            s.detail?.let { EventDetailSheet(it, onDismiss = { viewModel.select(null) }) }
        }
    }
}

@Composable
private fun EventList(
    state: EventsUiState.Content,
    onSelect: (EventType) -> Unit,
    onRetryMayor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "mayor") { MayorCard(state.mayor, state.perkpocalypse, state.termStatus, onRetryMayor) }
        for ((title, cards) in listOf("Common" to state.common, "Seasonal" to state.seasonal, "Rare" to state.rare)) {
            if (cards.isEmpty()) continue
            item(key = "$title header") { SectionHeader(title) }
            // One card per section, rows split by dividers.
            item(key = title) {
                SkydexCard(Modifier.fillMaxWidth()) {
                    cards.forEachIndexed { index, card ->
                        if (index > 0) ListDivider()
                        EventRow(card, onClick = { onSelect(card.type) })
                    }
                }
            }
        }
        if ((state.common + state.seasonal + state.rare).any { it.crops != null }) {
            item(key = "credit") { CropsCredit() }
        }
    }
}

@Composable
private fun EventRow(card: EventCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(eventIcon(card.type))
        Column(Modifier.weight(1f)) {
            Text(card.type.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                card.status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (card.happeningNow) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            card.crops?.let { ContestCrops(it) }
        }
        if (card.happeningNow) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun MayorCard(
    mayor: UiState<MayorStatus>,
    perkpocalypse: Boolean,
    termStatus: String?,
    onRetry: () -> Unit,
) {
    SkydexCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (mayor) {
                UiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Loading the mayor…")
                }
                is UiState.Error -> {
                    Text("Mayor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Couldn't load the mayor: ${mayor.message}. Events that depend on perks are hidden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                is UiState.Content -> MayorContent(mayor.data, perkpocalypse, termStatus)
            }
        }
    }
}

@Composable
private fun MayorContent(status: MayorStatus, perkpocalypse: Boolean, termStatus: String?) {
    Text("Mayor ${status.mayor.name}", style = MaterialTheme.typography.titleMedium)
    if (perkpocalypse) {
        Text(
            "Perkpocalypse — timing unpredictable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        status.mayor.perks.forEach { perk ->
            key("perk/${status.electionYear}/${status.mayor.key}/${perk.name}") { PerkRow(perk) }
        }
    }
    status.minister?.let { minister ->
        Text("Minister ${minister.name}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
        key("minister/${status.electionYear}/${minister.key}/${minister.perk.name}") { PerkRow(minister.perk) }
    }
    if (termStatus != null) {
        Text(termStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    remember(status) { electionSection(status) }?.let { section ->
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        // Keyed so the section starts collapsed again when voting closes or a new election opens.
        key(section.key) {
            Collapsible(section.title, "results", titleStyle = MaterialTheme.typography.titleSmall) {
                section.candidates.forEach {
                    Text(
                        "${it.name} · ${it.percent}%",
                        Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A perk's name; tapping it shows or hides its description. Call inside [key] so state stays with the perk. */
@Composable
private fun PerkRow(perk: Perk) {
    Collapsible(perk.name, "description") {
        Text(
            perk.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A [title] row with a chevron that shows or hides [content], collapsed by default. State is saved by position, so
 * call it inside [key] wherever it's repeated or its subject can change.
 */
@Composable
private fun Collapsible(
    title: String,
    actionNoun: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide $actionNoun" else "Show $actionNoun",
                    role = Role.Button,
                ) {
                    expanded = !expanded
                }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.Top,
        ) {
            Text(title, Modifier.weight(1f).padding(vertical = 12.dp), style = titleStyle)
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(bottom = 8.dp), content = content) }
    }
}

/** A contest's crops as icons, or a note while the new year's crops aren't out yet. */
@Composable
private fun ContestCrops(crops: List<Crop>) {
    if (crops.isEmpty()) {
        Text(
            "New year · crops not out yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        Modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = cropsLabel(crops) },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        crops.forEach { PixelIcon(cropIcon(it), size = 20.dp) }
    }
}

@Composable
private fun CropsCredit(modifier: Modifier = Modifier) {
    Text(
        "Contest crops: elitebot.dev",
        modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(detail: EventDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(eventIcon(detail.type), size = 40.dp)
                    Text(detail.type.displayName, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (detail.rows.isEmpty()) {
                item { Text("No upcoming occurrences known.", Modifier.padding(horizontal = 16.dp)) }
            }
            itemsIndexed(detail.rows, key = { _, row -> row.next.startsAt }) { index, row ->
                if (index > 0) ListDivider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        row.status,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (row.happeningNow) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                    row.crops?.let { ContestCrops(it) }
                }
            }
            if (detail.rows.any { it.crops != null }) {
                item { CropsCredit(Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }
}
