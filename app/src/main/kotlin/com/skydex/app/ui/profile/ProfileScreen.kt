package com.skydex.app.ui.profile

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.ListDivider
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.MessageView
import com.skydex.app.ui.common.SectionHeader
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.ui.common.formatCoins
import com.skydex.app.ui.common.titleCase
import com.skydex.app.ui.theme.SkillMaxed
import com.skydex.app.ui.theme.SkillProgress
import com.skydex.app.ui.theme.SkillTrack
import com.skydex.shared.model.Leveling
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.SlayerLevel
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
    val skillsOff = skillsApiOff(profile)
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(profile, state.offline) }
            item { SectionHeader("Skills") }
            item(key = "skills") { SkillGrid(profile.skills, skillsOff) }
            item { StatsStrip(profile, skillsOff) }
            profile.catacombs?.let { catacombs ->
                item { SectionHeader("Dungeons") }
                item { SkillTile(catacombs, Modifier.fillMaxWidth()) }
            }
            if (profile.slayers.isNotEmpty()) {
                item { SectionHeader("Slayers") }
                item { Slayers(profile.slayers) }
            }
        }
    }
}

@Composable
private fun Header(profile: SkyblockProfile, offline: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    profile.rank?.let {
                        RankBadge(it)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        profile.username,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append("${profile.cuteName} · Level ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(String.format(Locale.ROOT, "%.2f", profile.skyblockLevel))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            PlayerFace(profile.face)
        }
        if (offline) {
            SkydexCard(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text(
                    "Offline · last updated ${DateUtils.getRelativeTimeSpanString(profile.fetchedAt)}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** The skill tiles, two per row, under an [ApiOffOverlay] when the player has the Skills API off. */
@Composable
private fun SkillGrid(skills: List<SkillLevel>, apiOff: Boolean) {
    Box {
        // One list item so the overlay can cover the whole grid; the tiles behind it are hidden from accessibility.
        Column(
            if (apiOff) Modifier.clearAndSetSemantics {} else Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Two tiles per row: a Row per pair rather than a lazy grid nested in the list.
            displaySkills(skills).chunked(2).forEach { pair ->
                // Same-height tiles even if one side's text is taller (e.g. larger font scale).
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { SkillTile(it, Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (apiOff) ApiOffOverlay(Modifier.matchParentSize())
    }
}

/** A scrim with a lock explaining why the skills are hidden. No blur: it does nothing below API 31. */
@Composable
private fun ApiOffOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .semantics(mergeDescendants = true) {}
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Skills API off", style = MaterialTheme.typography.titleMedium)
            Text(
                "The player can turn it on in the SkyBlock menu under Settings, API Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Icon, name, level, a thin bar and the XP numbers; gold when maxed, green otherwise. */
@Composable
private fun SkillTile(skill: SkillLevel, modifier: Modifier = Modifier) {
    val color = skillColor(skill)
    SkydexCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkillIcon(skill.name)
                Column {
                    Text(
                        skill.name.titleCase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${skill.level}", style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
                        Text(
                            " / ${skill.maxLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { barProgress(skill) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = color,
                    trackColor = SkillTrack,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                // XP into this level on the left, towards the max on the right; the right one gives way first.
                xpLine(skill)?.let { xp ->
                    Row(
                        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = xp.description },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            xp.level,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            xp.total,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Purse, bank, fairy souls and skill average as a compact 2x2 grid. */
@Composable
private fun StatsStrip(profile: SkyblockProfile, skillsOff: Boolean) {
    SkydexCard(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell("Purse", formatCoins(profile.purse), Modifier.weight(1f))
            CellDivider()
            val bank = profile.bankBalance
            StatCell(
                "Bank",
                bank?.let(::formatCoins) ?: "API off",
                Modifier.weight(1f),
                color = if (bank == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        }
        ListDivider()
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell("Fairy souls", fairySoulsLabel(profile), Modifier.weight(1f))
            CellDivider()
            StatCell(
                "Skill avg",
                skillAverageLabel(profile),
                Modifier.weight(1f),
                color = if (skillsOff) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun CellDivider() {
    VerticalDivider(Modifier.fillMaxHeight(), 1.dp, MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Slayers(slayers: List<SlayerLevel>) {
    SkydexCard(Modifier.fillMaxWidth()) {
        slayers.forEachIndexed { index, slayer ->
            if (index > 0) ListDivider()
            // Bosses we don't have a table for fall back to the usual max of 9.
            val maxed = if (slayer.boss in Leveling.SLAYER_XP) {
                Leveling.slayerNextLevelXp(slayer.boss, slayer.experience) == null
            } else {
                slayer.level >= 9
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slayerName(slayer.boss),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Lv ${slayer.level}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (maxed) SkillMaxed else SkillProgress,
                )
                val (xp, max) = slayerXpLabel(slayer)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        xp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    max?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
