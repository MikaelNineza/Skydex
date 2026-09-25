package com.skydex.app.ui.profile

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView
import com.skydex.app.ui.common.formatCoins
import com.skydex.app.ui.common.titleCase
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SkyblockProfile
import java.util.Locale

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    when (val s = state) {
        ProfileUiState.Loading -> LoadingView(modifier)
        ProfileUiState.NoSelection -> PlayerSearch(search, viewModel::search, viewModel::pick, modifier)
        is ProfileUiState.Error -> MessageView(
            title = "Couldn't load the profile",
            body = s.message,
            actionLabel = "Retry",
            onAction = viewModel::refresh,
            modifier = modifier,
        )
        is ProfileUiState.Content -> ProfileContent(s, isRefreshing, viewModel::refresh, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    state: ProfileUiState.Content,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(profile, state.offline) }
            item { Overview(profile) }
            item { SectionTitle("Skills") }
            items(profile.skills, key = { it.name }) { SkillRow(it) }
            profile.catacombs?.let { catacombs ->
                item { SectionTitle("Dungeons") }
                item { SkillRow(catacombs) }
            }
            if (profile.slayers.isNotEmpty()) {
                item { SectionTitle("Slayers") }
                items(profile.slayers, key = { it.boss }) { slayer ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(slayer.boss.titleCase(), Modifier.weight(1f))
                        Text("Lv ${slayer.level} · ${formatCoins(slayer.experience.toDouble())} XP")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(profile: SkyblockProfile, offline: Boolean) {
    Column {
        Text(profile.username, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${profile.cuteName} · Skyblock level ${String.format(Locale.ROOT, "%.2f", profile.skyblockLevel)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offline) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            ) {
                Text(
                    "Offline · last updated ${DateUtils.getRelativeTimeSpanString(profile.fetchedAt)}",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun Overview(profile: SkyblockProfile) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatLine("Purse", formatCoins(profile.purse))
            StatLine("Bank", profile.bankBalance?.let(::formatCoins) ?: "Banking API off")
            StatLine("Fairy souls", profile.fairySouls.toString())
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SkillRow(skill: SkillLevel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(skill.name.titleCase(), Modifier.weight(1f))
            Text(if (skill.level >= skill.maxLevel) "${skill.level} (max)" else "${skill.level} / ${skill.maxLevel}")
        }
        LinearProgressIndicator(
            progress = { skill.progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
