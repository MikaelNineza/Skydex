package com.skydex.app.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.updates.InstallStep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val COLLAPSED_NOTE_LINES = 6

/** Settings' "App" card: the installed version, "Check for updates", and the latest release with its notes. */
@Composable
fun UpdateSection(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // The permission dialog is hosted by UpdateBanner, which is always composed above the tabs; a second one here
    // would stack two dialogs.

    SkydexCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${state.installedVersion}", Modifier.weight(1f))
                TextButton(onClick = viewModel::check, enabled = !state.checking) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp).semantics { contentDescription = "Checking for updates" },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Check for updates")
                    }
                }
            }
            state.checkError?.let { Text(it, Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.error) }

            // The skipped version is ignored here: Settings always offers the latest.
            val release = state.available
            if (release == null) {
                if (state.checked && state.checkError == null) {
                    Text("You're up to date", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            Column(Modifier.padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Skydex ${release.versionName} is available", style = MaterialTheme.typography.titleSmall)
                release.publishedAt?.let {
                    Text(
                        "Released ${releaseDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                release.notes?.let { ReleaseNotes(it) }
            }
            if (state.install == InstallStep.Idle || state.install == InstallStep.NeedsPermission) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { uriHandler.openReleasePage(release.releaseUrl) }) { Text("Release page") }
                    if (state.canInstallInApp) {
                        Button(onClick = viewModel::download) { Text("Download") }
                    } else {
                        Button(onClick = { uriHandler.openReleasePage(release.releaseUrl) }) { Text("View on GitHub") }
                    }
                }
            } else {
                InstallProgress(state, viewModel, Modifier.padding(top = 8.dp, end = 8.dp))
            }
        }
    }
}

/** Release notes as plain text, collapsed to a few lines with a "Show more" toggle. */
@Composable
private fun ReleaseNotes(notes: String) {
    var expanded by remember(notes) { mutableStateOf(false) }
    var overflows by remember(notes) { mutableStateOf(false) }
    Text(
        notes,
        style = MaterialTheme.typography.bodySmall,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_NOTE_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
    if (overflows || expanded) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
    }
}

private fun releaseDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
