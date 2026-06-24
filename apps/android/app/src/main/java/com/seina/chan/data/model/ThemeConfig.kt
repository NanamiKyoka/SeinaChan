package com.seina.chan.data.model

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * 主题配置，以 token 名 → ARGB 色值的形式存储颜色。
 * 亮/暗色各自独立配置，互不依赖。
 */
@Serializable
data class ThemeConfig(
    val id: String,
    val name: String,
    val isBuiltin: Boolean = false,
    val lightColors: Map<String, Long> = emptyMap(),
    val darkColors: Map<String, Long> = emptyMap(),
) {
    fun toColorScheme(isDark: Boolean): ColorScheme {
        val c = if (isDark) darkColors else lightColors
        val base = if (isDark) DARK_BASE else LIGHT_BASE
        return base.copy(
            primary = c.getColorOr("primary", base.primary),
            onPrimary = Color.White,
            background = c.getColorOr("background", base.background),
            onBackground = c.getColorOr("onBackground", base.onBackground),
            surface = c.getColorOr("surface", base.surface),
            onSurface = c.getColorOr("onSurface", base.onSurface),
            surfaceVariant = c.getColorOr("surfaceVariant", base.surfaceVariant),
            onSurfaceVariant = c.getColorOr("onSurfaceVariant", base.onSurfaceVariant),
            outline = c.getColorOr("outline", base.outline),
            error = c.getColorOr("error", base.error),
            onError = Color.White,
        )
    }
}

private fun Map<String, Long>.getColorOr(key: String, default: Color): Color =
    this[key]?.let { Color(it) } ?: default

// ============================================================
// 内置预设主题
// ============================================================

private val LIGHT_BASE = lightColorScheme()
private val DARK_BASE = darkColorScheme()

val BUILTIN_THEMES: List<ThemeConfig> = listOf(
    // ── 星奈：紫金哥特，灵感来自角色设定 ──
    ThemeConfig(
        id = "seina",
        name = "星奈",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFF7B2D8E,
            "background" to 0xFFF8F4FA,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF1A0A24,
            "onSurface" to 0xFF1A0A24,
            "surfaceVariant" to 0xFFEDE4F2,
            "onSurfaceVariant" to 0xFF6C5B78,
            "outline" to 0xFFCBB5D4,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFFC684E0,
            "background" to 0xFF0D0A1A,
            "surface" to 0xFF1A1528,
            "onBackground" to 0xFFE8DDF0,
            "onSurface" to 0xFFE8DDF0,
            "surfaceVariant" to 0xFF2A2138,
            "onSurfaceVariant" to 0xFFA897B8,
            "outline" to 0xFF4A3D5A,
            "error" to 0xFFF2B8B5,
        )
    ),

    // ── 暖阳：当前 Claude 暖色 (保留) ──
    ThemeConfig(
        id = "warm-sun",
        name = "暖阳",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFFCC785C,
            "background" to 0xFFFAF9F5,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF141413,
            "onSurface" to 0xFF141413,
            "surfaceVariant" to 0xFFF0EEE8,
            "onSurfaceVariant" to 0xFF6C6A64,
            "outline" to 0xFFD0CEC8,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFFD48972,
            "background" to 0xFF181715,
            "surface" to 0xFF232220,
            "onBackground" to 0xFFE8E6E1,
            "onSurface" to 0xFFE8E6E1,
            "surfaceVariant" to 0xFF2D2B27,
            "onSurfaceVariant" to 0xFFA09D96,
            "outline" to 0xFF3D3B36,
            "error" to 0xFFF2B8B5,
        )
    ),

    // ── 夜曲：冷色深沉 ──
    ThemeConfig(
        id = "nocturne",
        name = "夜曲",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFF5B6ABF,
            "background" to 0xFFF0EEF2,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF1A1C2E,
            "onSurface" to 0xFF1A1C2E,
            "surfaceVariant" to 0xFFE4E2EC,
            "onSurfaceVariant" to 0xFF65637A,
            "outline" to 0xFFC4C2D0,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFF8B9BEB,
            "background" to 0xFF0F111A,
            "surface" to 0xFF1A1D2B,
            "onBackground" to 0xFFE0E2F0,
            "onSurface" to 0xFFE0E2F0,
            "surfaceVariant" to 0xFF252839,
            "onSurfaceVariant" to 0xFF9194AD,
            "outline" to 0xFF3D4057,
            "error" to 0xFFF2B8B5,
        )
    ),

    // ── 晨曦：明亮清爽 ──
    ThemeConfig(
        id = "dawn",
        name = "晨曦",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFFE8916A,
            "background" to 0xFFFEFCF8,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF2C221E,
            "onSurface" to 0xFF2C221E,
            "surfaceVariant" to 0xFFF5F0EC,
            "onSurfaceVariant" to 0xFF7A716A,
            "outline" to 0xFFD6CEC8,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFFF0B08C,
            "background" to 0xFF1C1816,
            "surface" to 0xFF282320,
            "onBackground" to 0xFFECE4DF,
            "onSurface" to 0xFFECE4DF,
            "surfaceVariant" to 0xFF332D29,
            "onSurfaceVariant" to 0xFFA69C97,
            "outline" to 0xFF4A423D,
            "error" to 0xFFF2B8B5,
        )
    ),

    // ── 极光：青绿自然 ──
    ThemeConfig(
        id = "aurora",
        name = "极光",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFF2D8E7B,
            "background" to 0xFFF4FAF8,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF142420,
            "onSurface" to 0xFF142420,
            "surfaceVariant" to 0xFFE6F0EC,
            "onSurfaceVariant" to 0xFF5C7A72,
            "outline" to 0xFFB8CDC6,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFF5CC4B0,
            "background" to 0xFF0D1A17,
            "surface" to 0xFF162622,
            "onBackground" to 0xFFDDF0EA,
            "onSurface" to 0xFFDDF0EA,
            "surfaceVariant" to 0xFF1F332E,
            "onSurfaceVariant" to 0xFF7F9E96,
            "outline" to 0xFF355048,
            "error" to 0xFFF2B8B5,
        )
    ),

    // ── 胧月：柔和粉紫 ──
    ThemeConfig(
        id = "hazymoon",
        name = "胧月",
        isBuiltin = true,
        lightColors = mapOf(
            "primary" to 0xFFAD6B9E,
            "background" to 0xFFFDF8FA,
            "surface" to 0xFFFFFFFF,
            "onBackground" to 0xFF2D1C28,
            "onSurface" to 0xFF2D1C28,
            "surfaceVariant" to 0xFFF4ECF0,
            "onSurfaceVariant" to 0xFF7E6B77,
            "outline" to 0xFFD8CAD2,
            "error" to 0xFFB3261E,
        ),
        darkColors = mapOf(
            "primary" to 0xFFD494C6,
            "background" to 0xFF1C141A,
            "surface" to 0xFF281E24,
            "onBackground" to 0xFFF0E4EC,
            "onSurface" to 0xFFF0E4EC,
            "surfaceVariant" to 0xFF332830,
            "onSurfaceVariant" to 0xFFA28E9A,
            "outline" to 0xFF4D3D47,
            "error" to 0xFFF2B8B5,
        )
    ),
)

/** 默认内置主题 ID */
const val DEFAULT_THEME_ID = "warm-sun"

/** 自定义主题 ID */
const val CUSTOM_THEME_ID = "custom"

/** 字体组合预设 */
enum class FontPreset(val id: String, val displayName: String) {
    SerifSans("serif-sans", "默认 (衬线+无衬线)"),
    Sans("sans", "纯无衬线"),
    System("system", "系统字体"),
}
