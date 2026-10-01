package com.skydex.app.ui.updates

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.skydex.app.updates.InstallStep
import com.skydex.app.updates.UpdateDownloader
import com.skydex.app.updates.UpdateState
import java.util.Locale

/** Asks the user to let Skydex install apps when [state] needs it; [onResult] runs on return from system settings. */
@Composable
fun UpdatePermissionHandler(state: UpdateState, onResult: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    if (state.install != InstallStep.NeedsPermission) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Allow installs from Skydex") },
        text = {
            Text(
                "Android needs your permission for Skydex to install its own updates. " +
                    "Turn on 'Allow from this source', then come back.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                launcher.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                )
            }) { Text("Open settings") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * Download, install and failure states shared by the banner and the Settings section. Shows nothing while [state] is
 * idle or waiting for the install permission.
 */
@Composable
internal fun InstallProgress(state: UpdateState, viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    when (val step = state.install) {
        is InstallStep.Downloading -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { if (step.total > 0) step.bytesRead.toFloat() / step.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Downloading ${megabytes(step.bytesRead)} / ${megabytes(step.total)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::cancel) { Text("Cancel") }
            }
        }
        InstallStep.Installing -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Preparing the install…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InstallStep.WaitingForUser -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Confirm the install to finish updating", Modifier.weight(1f))
            TextButton(onClick = viewModel::showConfirmation) { Text("Install") }
        }
        is InstallStep.Failed -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(step.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::download) { Text("Retry") }
        }
        InstallStep.Idle, InstallStep.NeedsPermission -> Unit
    }
}

/** Opens a release page, falling back to Skydex's releases if [url] isn't one; a missing browser is ignored. */
internal fun UriHandler.openReleasePage(url: String) {
    runCatching { openUri(UpdateDownloader.releasePageUrl(url)) }
}

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
