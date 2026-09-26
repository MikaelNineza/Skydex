package com.skydex.app.ui.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skydex.app.R
import com.skydex.app.ui.common.PixelIcon

// Mayor faces are the 8x8 face + hat layer cropped from each mayor's Hypixel SkyBlock skin, upscaled to 128px and
// bundled in res/drawable-nodpi: the app never fetches them at runtime.

/** Bundled face for a mayor or minister, matched by name (keys like "economist" are less stable), or null. */
@DrawableRes
fun mayorFace(name: String): Int? = when (name.trim().lowercase()) {
    "aatrox" -> R.drawable.mayor_aatrox
    "barry" -> R.drawable.mayor_barry
    "cole" -> R.drawable.mayor_cole
    "derpy" -> R.drawable.mayor_derpy
    "diana" -> R.drawable.mayor_diana
    "diaz" -> R.drawable.mayor_diaz
    "finnegan" -> R.drawable.mayor_finnegan
    "foxy" -> R.drawable.mayor_foxy
    "jerry" -> R.drawable.mayor_jerry
    "marina" -> R.drawable.mayor_marina
    "paul" -> R.drawable.mayor_paul
    "scorpius" -> R.drawable.mayor_scorpius
    else -> null
}

/** A mayor's face, or a generic person icon for a mayor added after this build. Decorative: the name sits beside it. */
@Composable
fun MayorFace(name: String, size: Dp = 24.dp) {
    val face = mayorFace(name)
    if (face != null) {
        // Faces are 8x8 blocks of flat colour, so nearest-neighbour keeps their edges crisp.
        PixelIcon(face, size = size, filterQuality = FilterQuality.None)
    } else {
        Icon(Icons.Filled.Person, null, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
