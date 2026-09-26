package com.skydex.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

// Building blocks for the app's card style: a tonal surface with a subtle 1dp outline. Lists are one card per group,
// with a ListDivider between rows (not after the last one).

private val CardShape = RoundedCornerShape(16.dp)

/** The app's card. */
@Composable
fun SkydexCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** Small uppercase label above a group of cards. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        modifier.fillMaxWidth().padding(top = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Separator between rows inside a [SkydexCard]. */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = 16.dp), 1.dp, MaterialTheme.colorScheme.outlineVariant)
}

/** Bundled pixel-art render, filtered so downscaling doesn't look jagged. Decorative unless [contentDescription] is given. */
@Composable
fun PixelIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = null,
    // The bundled renders are 128px; nearest-neighbour downscaling would drop pixels and look jagged.
    filterQuality: FilterQuality = FilterQuality.Medium,
) {
    Image(
        ImageBitmap.imageResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        filterQuality = filterQuality,
    )
}
