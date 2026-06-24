package com.seina.chan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.seina.chan.data.model.ThemeConfig

@Composable
fun SeinaChanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeConfig: ThemeConfig? = null,
    fontPresetId: String = "serif-sans",
    content: @Composable () -> Unit
) {
    val colorScheme = themeConfig?.toColorScheme(darkTheme)
        ?: if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyForPreset(fontPresetId),
        shapes = SeinaChanMaterialShapes,
        content = content
    )
}
