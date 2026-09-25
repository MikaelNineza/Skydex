package com.skydex.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView

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
        is EventsUiState.Content -> if (s.rows.isEmpty()) {
            MessageView("No events in the next 24 hours", modifier = modifier)
        } else {
            EventList(s.rows, modifier)
        }
    }
}

@Composable
private fun EventList(rows: List<EventRow>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Next 24 hours", style = MaterialTheme.typography.titleLarge) }
        items(rows, key = { "${it.event.type}-${it.event.startsAt}" }) { row ->
            val colors = if (row.happeningNow) {
                CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.elevatedCardColors()
            }
            ElevatedCard(Modifier.fillMaxWidth(), colors = colors) {
                Column(Modifier.padding(16.dp)) {
                    Text(row.event.type.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(row.status, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
