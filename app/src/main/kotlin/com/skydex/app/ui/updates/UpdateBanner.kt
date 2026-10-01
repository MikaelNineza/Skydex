package com.skydex.app.ui.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.updates.InstallStep

/**
 * "Skydex X.Y.Z is available" above every tab, with Download / Skip, and the download's progress once started.
 * With [showInstallProgress] false (on Settings, whose update section shows it) the banner hides once an install starts.
 */
@Composable
fun UpdateBanner(
    modifier: Modifier = Modifier,
    showInstallProgress: Boolean = true,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // Hosted here (always composed) for both the banner and the Settings section.
    UpdatePermissionHandler(state, onResult = viewModel::onPermissionResult, onCancel = viewModel::cancel)

    val idle = state.install == InstallStep.Idle || state.install == InstallStep.NeedsPermission
    AnimatedVisibility(state.showBanner && (idle || showInstallProgress), modifier) {
        val release = state.available ?: return@AnimatedVisibility
        SkydexCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Skydex ${release.versionName} is available",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val step = state.install
                    if (step == InstallStep.Idle || step is InstallStep.Failed) {
                        IconButton(onClick = viewModel::dismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                        }
                    }
                }
                if (idle) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { viewModel.skip(release.versionCode) }) { Text("Skip this version") }
                        if (state.canInstallInApp) {
                            Button(onClick = viewModel::download) { Text("Download") }
                        } else {
                            Button(onClick = { uriHandler.openReleasePage(release.releaseUrl) }) { Text("View on GitHub") }
                        }
                    }
                } else {
                    InstallProgress(state, viewModel, Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}
