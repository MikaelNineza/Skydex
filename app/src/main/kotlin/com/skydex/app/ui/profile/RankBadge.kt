package com.skydex.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydex.shared.model.PlayerRank

/** "#RRGGBB" (the # is optional) as an opaque colour, or null if it isn't one. */
internal fun parseRankColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    if (digits.length != 6 || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

/**
 * A SkyCrypt-style rank pill: the rank name on its colour, then the pluses (if any) on theirs, split by a slanted edge.
 * TalkBack reads it as e.g. "Rank MVP+".
 */
@Composable
fun RankBadge(rank: PlayerRank, modifier: Modifier = Modifier) {
    val plus = rank.plus
    Row(
        modifier
            .heightIn(min = 22.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(50))
            .semantics(mergeDescendants = true) { contentDescription = "Rank ${rank.name}${plus.orEmpty()}" },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .background(parseRankColor(rank.color) ?: Color.Gray)
                // Extra room on the right for the plus segment's slant, which reaches back over this one.
                .padding(start = 8.dp, end = if (plus != null) 10.dp else 8.dp, top = 2.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            BadgeText(rank.name)
        }
        if (plus != null) {
            val plusColor = rank.plusColor?.let(::parseRankColor) ?: Color.Gray
            Box(
                Modifier
                    .fillMaxHeight()
                    .drawBehind {
                        // Reaches back over the name segment with a 20° slant, like SkyCrypt's skewed ::before.
                        val slant = size.height * 0.182f
                        val overlap = 5.dp.toPx()
                        val path = Path().apply {
                            moveTo(-overlap + slant, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(-overlap - slant, size.height)
                            close()
                        }
                        drawPath(path, plusColor)
                    }
                    .padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                BadgeText(plus)
            }
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        maxLines = 1,
        // Keeps white text readable on light badge colours like yellow (#EFC721).
        style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(1f, 1f), 1f)),
    )
}
