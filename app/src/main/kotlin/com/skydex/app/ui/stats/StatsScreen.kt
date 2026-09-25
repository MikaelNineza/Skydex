package com.skydex.app.ui.stats

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView
import com.skydex.app.ui.common.formatCoins
import com.skydex.shared.model.StatsHistory
import java.util.Locale

@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()

    if (state == StatsUiState.NoSelection) {
        MessageView("No profile selected", body = "Pick a profile on the Profile tab first.", modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (range in StatsViewModel.RANGES) {
                FilterChip(
                    selected = range == days,
                    onClick = { viewModel.setDays(range) },
                    label = { Text("$range days") },
                )
            }
        }
        when (val s = state) {
            StatsUiState.Loading, StatsUiState.NoSelection -> LoadingView()
            is StatsUiState.Error -> MessageView(
                title = "Couldn't load history",
                body = s.message,
                actionLabel = "Retry",
                onAction = viewModel::retry,
            )
            is StatsUiState.Content -> if (s.history.points.isEmpty()) {
                MessageView(
                    title = "No history yet",
                    body = "The server takes snapshots of your profile once you turn on " +
                        "\"Track this profile's history\" in Settings. Charts appear after the first ones.",
                )
            } else {
                Charts(s.history)
            }
        }
    }
}

@Composable
private fun Charts(history: StatsHistory) {
    val context = LocalContext.current
    val formatDate = { millis: Long ->
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChartCard("Skyblock level") {
            LineChart(
                points = history.points.map { ChartPoint(it.takenAt, it.skyblockLevel) },
                formatValue = { String.format(Locale.ROOT, "%.1f", it) },
                formatDate = formatDate,
            )
        }
        ChartCard("Purse + bank") {
            LineChart(
                points = history.points.map { ChartPoint(it.takenAt, it.purse + (it.bankBalance ?: 0.0)) },
                formatValue = ::formatCoins,
                formatDate = formatDate,
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
