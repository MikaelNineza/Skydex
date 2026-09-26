package com.skydex.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    // Tabular figures ("tnum") on the styles that show numbers: levels and coins line up, and event countdowns
    // (bodyMedium on the Events list, titleSmall in the detail sheet, bodySmall for the mayor's term) don't jitter
    // as they tick.
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        fontFeatureSettings = "tnum",
    ),
    titleSmall = Default.titleSmall.copy(fontFeatureSettings = "tnum"),
    bodyMedium = Default.bodyMedium.copy(fontFeatureSettings = "tnum"),
    bodySmall = Default.bodySmall.copy(fontFeatureSettings = "tnum"),
    // Uppercase section headers.
    labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp),
)
