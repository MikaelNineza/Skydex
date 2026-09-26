package com.skydex.app.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skydex.app.R
import com.skydex.app.ui.common.PixelIcon
import java.util.Locale

// Skill and event icons (ui/events/EventIcons.kt) are SkyCrypt's item renders (sky.shiiyu.moe/api/item/<ID>),
// downloaded once and bundled in res/drawable-nodpi: the app never fetches them at runtime.

/** Bundled icon for a skill (or "catacombs"), or null if we don't have one. */
@DrawableRes
fun skillIcon(name: String): Int? = when (name) {
    "farming" -> R.drawable.skill_farming
    "mining" -> R.drawable.skill_mining
    "combat" -> R.drawable.skill_combat
    "foraging" -> R.drawable.skill_foraging
    "fishing" -> R.drawable.skill_fishing
    "enchanting" -> R.drawable.skill_enchanting
    "alchemy" -> R.drawable.skill_alchemy
    "carpentry" -> R.drawable.skill_carpentry
    "taming" -> R.drawable.skill_taming
    "hunting" -> R.drawable.skill_hunting
    "runecrafting" -> R.drawable.skill_runecrafting
    "social" -> R.drawable.skill_social
    "catacombs" -> R.drawable.skill_catacombs
    else -> null
}

/** 28dp pixel-art icon; skills without one get a circle with their first letter. */
@Composable
fun SkillIcon(name: String, modifier: Modifier = Modifier) {
    val id = skillIcon(name)
    if (id != null) {
        PixelIcon(id, modifier)
    } else {
        Box(
            modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
