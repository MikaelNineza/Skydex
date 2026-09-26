package com.skydex.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skydex.app.ui.common.ListDivider
import com.skydex.app.ui.common.SkydexCard
import com.skydex.app.ui.common.UiState
import com.skydex.app.ui.common.titleCase
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.ProfileSummary

/** Username search, then a list of that player's profiles to pick from. */
@Composable
fun PlayerSearch(
    search: UiState<PlayerProfiles>?,
    onSearch: (String) -> Unit,
    onPick: (PlayerProfiles, ProfileSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Find your Skyblock profile", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Minecraft username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            trailingIcon = {
                IconButton(onClick = { onSearch(query) }) { Icon(Icons.Filled.Search, "Search") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when (search) {
            null -> Unit
            UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            is UiState.Error -> Text(search.message, color = MaterialTheme.colorScheme.error)
            is UiState.Content -> ProfileList(search.data, onPick)
        }
    }
}

@Composable
private fun ProfileList(player: PlayerProfiles, onPick: (PlayerProfiles, ProfileSummary) -> Unit) {
    if (player.profiles.isEmpty()) {
        Text("${player.username} has no Skyblock profiles.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${player.username}'s profiles", style = MaterialTheme.typography.titleMedium) }
        item {
            SkydexCard(Modifier.fillMaxWidth()) {
                player.profiles.forEachIndexed { index, profile ->
                    if (index > 0) ListDivider()
                    ProfileRow(profile, onClick = { onPick(player, profile) })
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfileSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.cuteName, style = MaterialTheme.typography.titleMedium)
            Text(
                profile.gameMode?.titleCase() ?: "Normal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (profile.selected) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "Last played",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
