package com.skydex.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView
import com.skydex.app.ui.common.UiState
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
        for ((title, cards) in listOf("Common" to state.common, "Rare & seasonal" to state.rare)) {
            if (cards.isEmpty()) continue
            item(key = title) { Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge) }
            items(cards, key = { it.type }) { card ->
                val colors = if (card.happeningNow) {
                    CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.elevatedCardColors()
                }
                ElevatedCard(onClick = { onSelect(card.type) }, modifier = Modifier.fillMaxWidth(), colors = colors) {
                    Column(Modifier.padding(16.dp)) {
                        Text(card.type.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(card.status, style = MaterialTheme.typography.bodyMedium)
                        card.crops?.let { CropsText(it) }
                    }
                }
            }
        }
        if ((state.common + state.rare).any { it.crops != null }) {
            item(key = "credit") { CropsCredit() }
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
    ElevatedCard(Modifier.fillMaxWidth()) {
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
        status.mayor.perks.forEach { PerkText(it) }
    }
    status.minister?.let { minister ->
        Text("Minister ${minister.name}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
        PerkText(minister.perk)
    }
    if (termStatus != null) {
        Text(termStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (status.candidates.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "Voting open · Year ${status.votingYear ?: status.electionYear + 1}",
            style = MaterialTheme.typography.titleSmall,
        )
        val totalVotes = status.candidates.sumOf { it.votes }.coerceAtLeast(1)
        status.candidates.sortedByDescending { it.votes }.forEach { candidate ->
            Column(Modifier.padding(top = 4.dp)) {
                Text("${candidate.name} · ${candidate.votes * 100 / totalVotes}%")
                Text(
                    candidate.perks.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PerkText(perk: Perk) {
    Column {
        Text(perk.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            perk.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CropsText(crops: List<Crop>) {
    Text(
        if (crops.isEmpty()) "New year · crops not out yet" else crops.joinToString { it.displayName },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CropsCredit() {
    Text(
        "Contest crops: elitebot.dev",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(detail: EventDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(detail.type.displayName, style = MaterialTheme.typography.titleLarge) }
            if (detail.rows.isEmpty()) {
                item { Text("No upcoming occurrences known.") }
            }
            items(detail.rows, key = { it.next.startsAt }) { row ->
                Column {
                    Text(row.dateLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        row.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.happeningNow) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    row.crops?.let { CropsText(it) }
                }
            }
            if (detail.rows.any { it.crops != null }) {
                item { CropsCredit() }
            }
        }
    }
}
