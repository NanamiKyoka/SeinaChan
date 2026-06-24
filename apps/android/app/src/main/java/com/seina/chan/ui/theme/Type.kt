package com.seina.chan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// === Font Families ===
// TODO: Replace with custom fonts by placing .ttf files in res/font/ and using Font(R.font.xxx).
// For now, using system font families as fallback to ensure compilation.

val CormorantGaramond = FontFamily.Serif

val Inter = FontFamily.SansSerif

val JetBrainsMono = FontFamily.Monospace

// === Text Styles (named per DESIGN.md) ===
object TextStyles {
    val displayXl = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.12).sp,
    )

    val displayLg = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
    )

    val displayMd = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.64).sp,
    )

    val displaySm = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.48).sp,
    )

    val bodyLg = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.09).sp,
    )

    val bodyMd = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.08).sp,
    )

    val bodySm = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.07).sp,
    )

    val caption = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    val label = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.sp,
    )

    val code = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
}

// === Material3 Typography mapping ===
val SeinaChanTypography = Typography(
    displayLarge = TextStyles.displayXl,
    displayMedium = TextStyles.displayLg,
    displaySmall = TextStyles.displayMd,
    headlineLarge = TextStyles.displaySm,
    bodyLarge = TextStyles.bodyLg,
    bodyMedium = TextStyles.bodyMd,
    bodySmall = TextStyles.bodySm,
    labelSmall = TextStyles.caption,
    labelMedium = TextStyles.label,
)

/**
 * 根据字体预设 ID 选择对应的 Typography。
 * - "serif-sans": 默认，展示用衬线 (Cormorant Garamond) + 正文无衬线 (Inter)
 * - "sans": 纯无衬线 (Inter)
 * - "system": 系统字体
 */
fun typographyForPreset(presetId: String): Typography {
    val display = when (presetId) {
        "serif-sans" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        else -> FontFamily.SansSerif
    }
    val body = when (presetId) {
        "serif-sans" -> FontFamily.SansSerif
        "sans" -> FontFamily.SansSerif
        else -> FontFamily.SansSerif
    }
    return Typography(
        displayLarge = TextStyles.displayXl.copy(fontFamily = display),
        displayMedium = TextStyles.displayLg.copy(fontFamily = display),
        displaySmall = TextStyles.displayMd.copy(fontFamily = display),
        headlineLarge = TextStyles.displaySm.copy(fontFamily = display),
        bodyLarge = TextStyles.bodyLg.copy(fontFamily = body),
        bodyMedium = TextStyles.bodyMd.copy(fontFamily = body),
        bodySmall = TextStyles.bodySm.copy(fontFamily = body),
        labelSmall = TextStyles.caption.copy(fontFamily = body),
        labelMedium = TextStyles.label.copy(fontFamily = body),
    )
}
