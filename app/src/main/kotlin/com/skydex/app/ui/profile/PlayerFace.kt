package com.skydex.app.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The player's skin face as the server sent it (64 ARGB ints, 8x8), scaled up with hard pixel edges, or a generic
 * person icon when there is none. Decorative: the username sits beside it.
 */
@Composable
fun PlayerFace(face: List<Int>?, size: Dp = 48.dp) {
    if (face?.size == 64) {
        val bitmap = remember(face) {
            Bitmap.createBitmap(face.toIntArray(), 8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        Image(
            bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)),
            filterQuality = FilterQuality.None,
        )
    } else {
        Icon(Icons.Filled.Person, null, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
