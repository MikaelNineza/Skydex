package com.skydex.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.ListDivider
import com.skydex.app.ui.common.LoadingView
import com.skydex.app.ui.common.PixelIcon
import com.skydex.app.ui.common.SectionHeader
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.ui.common.cropIcon
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventCategory
import com.skydex.shared.model.EventType

@Composable
fun SettingsScreen(
    onChangePlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return LoadingView(modifier)
    val settings = s.settings

    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionDenied = !it
    }
    fun onEventToggle(type: EventType, enabled: Boolean) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (enabled && needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.setEventEnabled(type, enabled)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Profile") }
        item {
            SkydexCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val selection = settings.selection
                    Text(selection?.let { "${it.username} · ${it.cuteName}" } ?: "No profile selected")
                    OutlinedButton(
                        onClick = {
                            viewModel.changePlayer()
                            onChangePlayer()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Change player or profile") }
                }
                ListDivider()
                SwitchRow(
                    title = "Track this profile's history",
                    subtitle = "The server takes regular snapshots for the Stats tab.",
                    checked = settings.trackHistory,
                    enabled = settings.selection != null,
                    onCheckedChange = viewModel::setTrackHistory,
                )
                s.syncError?.let { error ->
                    ListDivider()
                    Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::retrySync) { Text("Retry") }
                    }
                }
            }
        }
        if (!s.pushConfigured) {
            item { SectionHeader("Event notifications") }
            item { Text("Push notifications not configured", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            if (permissionDenied) {
                item {
                    Text(
                        "Notifications are blocked. Allow them in system settings to get event alerts.",
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val groups = listOf(
                EventCategory.COMMON to "Common",
                EventCategory.SEASONAL to "Seasonal",
                EventCategory.RARE to "Rare",
            )
            for ((category, title) in groups) {
                item { SectionHeader("$title event alerts") }
                item {
                    SkydexCard(Modifier.fillMaxWidth()) {
                        EventType.entries.filter { it.category == category }.forEachIndexed { index, type ->
                            if (index > 0) ListDivider()
                            SwitchRow(
                                title = type.displayName,
                                checked = type in settings.subscribedEvents,
                                onCheckedChange = { onEventToggle(type, it) },
                            )
                            if (type == EventType.JACOBS_CONTEST && type in settings.subscribedEvents) {
                                CropFilter(settings.jacobCrops, viewModel::setCropEnabled)
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Notify me before an event starts") }
            item {
                SkydexCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (minutes in SettingsViewModel.LEAD_MINUTES) {
                            FilterChip(
                                selected = settings.leadMinutes == minutes,
                                onClick = { viewModel.setLeadMinutes(minutes) },
                                label = { Text("$minutes min") },
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Skill, event and crop icons are item renders from SkyCrypt (sky.shiiyu.moe) of Minecraft and " +
                    "Hypixel SkyBlock textures; all rights remain with their respective owners. Contest crop data " +
                    "from elitebot.dev. Skydex is not affiliated with or endorsed by Mojang or Hypixel.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Which crops a Jacob's contest must include to be worth a push. */
@Composable
private fun CropFilter(selected: Set<Crop>, onToggle: (Crop, Boolean) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Text(
            "No crops selected = any crop",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (crop in Crop.entries) {
                CropToggle(crop, crop in selected) { onToggle(crop, it) }
            }
        }
    }
}

/** An icon-only crop checkbox: TalkBack reads e.g. "Wheat, checkbox, checked". */
@Composable
private fun CropToggle(crop: Crop, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .background(if (selected) colorScheme.primaryContainer else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) colorScheme.primary else colorScheme.outline,
                shape,
            )
            .toggleable(selected, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = crop.displayName },
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(cropIcon(crop), size = 32.dp, modifier = Modifier.alpha(if (selected) 1f else 0.75f))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    // The whole row toggles (bigger touch target, one TalkBack node); the Switch itself is display-only.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
