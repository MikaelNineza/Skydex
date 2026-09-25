package com.skydex.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.LoadingView
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
        item { SectionTitle("Profile") }
        item {
            val selection = settings.selection
            Text(if (selection == null) "No profile selected" else "${selection.username} · ${selection.cuteName}")
            OutlinedButton(
                onClick = {
                    viewModel.changePlayer()
                    onChangePlayer()
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Change player or profile") }
        }
        item {
            SwitchRow(
                title = "Track this profile's history",
                subtitle = "The server takes regular snapshots for the Stats tab.",
                checked = settings.trackHistory,
                enabled = settings.selection != null,
                onCheckedChange = viewModel::setTrackHistory,
            )
        }
        s.syncError?.let { error ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retrySync) { Text("Retry") }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Event notifications") }
        if (!s.pushConfigured) {
            item { Text("Push notifications not configured", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            if (permissionDenied) {
                item {
                    Text(
                        "Notifications are blocked. Allow them in system settings to get event alerts.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            items(EventType.entries) { type ->
                SwitchRow(
                    title = type.displayName,
                    checked = type in settings.subscribedEvents,
                    onCheckedChange = { onEventToggle(type, it) },
                )
            }
            item {
                Text("Notify me before an event starts", Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
