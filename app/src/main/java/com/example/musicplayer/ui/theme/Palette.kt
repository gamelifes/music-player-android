/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

/**
 * 四套可切换的强调色。只决定主色/辅色，不决定底色 ——
 * 底色由用户在取色器里自由选择（或跟随主题）。
 *
 * primary 都是浅色，所以 onPrimary 必须是深色；
 * 自定义浅色底时会在 [scheme] 里自动换成加深版的主色，保证对比度。
 */
enum class AppPalette(
    val id: String,
    val label: String,
    val en: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val brand: Color,
) {
    CELADON(
        id = "celadon",
        label = "青瓷",
        en = "CELADON",
        description = "青绿主色，像音频设备面板",
        background = Color(0xFF0B1113),
        surface = Color(0xFF141E21),
        surfaceVariant = Color(0xFF1E2C2F),
        primary = Color(0xFF8FE3D6),
        secondary = Color(0xFFA9C7C4),
        tertiary = Color(0xFFC9B6FF),
        onPrimary = Color(0xFF06201E),
        onBackground = Color(0xFFEAF7F5),
        onSurfaceVariant = Color(0xFF94AAA7),
        brand = Color(0xFF14514E),
    ),

    GRAPE(
        id = "grape",
        label = "暮色紫",
        en = "GRAPE DUSK",
        description = "品牌紫，最接近原版",
        background = Color(0xFF121016),
        surface = Color(0xFF1B1920),
        surfaceVariant = Color(0xFF262130),
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8),
        onPrimary = Color(0xFF241A3A),
        onBackground = Color(0xFFF2EDFB),
        onSurfaceVariant = Color(0xFFA29CB0),
        brand = Color(0xFF6650A4),
    ),

    INDIGO(
        id = "indigo",
        label = "午夜靛",
        en = "INDIGO NIGHT",
        description = "冷蓝 + 青色，克制耐看",
        background = Color(0xFF0D1017),
        surface = Color(0xFF171B24),
        surfaceVariant = Color(0xFF212836),
        primary = Color(0xFFAEC6FF),
        secondary = Color(0xFFB9C3DA),
        tertiary = Color(0xFF8FE3D6),
        onPrimary = Color(0xFF0A1A3A),
        onBackground = Color(0xFFEDF1FA),
        onSurfaceVariant = Color(0xFF9AA5B8),
        brand = Color(0xFF2E4A8F),
    ),

    COPPER(
        id = "copper",
        label = "暖铜",
        en = "COPPER",
        description = "黑胶与磁带的模拟味",
        background = Color(0xFF141110),
        surface = Color(0xFF1F1A17),
        surfaceVariant = Color(0xFF2C2521),
        primary = Color(0xFFF2C795),
        secondary = Color(0xFFD8BFA5),
        tertiary = Color(0xFFE98F6E),
        onPrimary = Color(0xFF2A1808),
        onBackground = Color(0xFFF7EFE6),
        onSurfaceVariant = Color(0xFFB3A497),
        brand = Color(0xFF7A4630),
    );

    /**
     * [customBackground] 为 null 时使用主题自带底色；
     * 否则由取色器选中的颜色推导 surface / onBackground 等一整套 token。
     */
    fun scheme(customBackground: Color? = null): ColorScheme {
        val isCustom = customBackground != null
        val bg = customBackground ?: background
        val light = bg.isLightBackground()

        val surf = when {
            !isCustom -> surface
            light -> lerp(bg, Color.White, 0.55f)
            else -> lerp(bg, Color.White, 0.07f)
        }
        val surfVariant = when {
            !isCustom -> surfaceVariant
            light -> lerp(bg, Color.Black, 0.06f)
            else -> lerp(bg, Color.White, 0.13f)
        }
        val onBg = when {
            !isCustom -> onBackground
            light -> lerp(bg, Color.Black, 0.88f)
            else -> lerp(bg, Color.White, 0.92f)
        }
        val onSurfVariant = when {
            !isCustom -> onSurfaceVariant
            light -> lerp(bg, Color.Black, 0.62f)
            else -> lerp(bg, Color.White, 0.72f)
        }

        // 浅色底上，粉彩主色会看不清：保留色相、提高饱和度、压低明度，
        // 比直接混黑更「有色」，选中态/进度条和灰色图标能明显区分。
        val accentPrimary = if (light) primary.vividOnLight() else primary
        val accentSecondary = if (light) secondary.vividOnLight() else secondary
        val accentTertiary = if (light) tertiary.vividOnLight() else tertiary
        val accentOnPrimary = if (light) Color.White else onPrimary

        return darkColorScheme(
            primary = accentPrimary,
            onPrimary = accentOnPrimary,
            primaryContainer = accentPrimary.copy(alpha = 0.16f),
            onPrimaryContainer = accentPrimary,
            inversePrimary = if (isCustom) accentPrimary else brand,
            secondary = accentSecondary,
            onSecondary = if (light) Color.White else Color(0xFF101418),
            secondaryContainer = surfVariant,
            onSecondaryContainer = onBg,
            tertiary = accentTertiary,
            onTertiary = if (light) Color.White else Color(0xFF1A1030),
            tertiaryContainer = accentTertiary.copy(alpha = 0.16f),
            onTertiaryContainer = accentTertiary,
            background = bg,
            onBackground = onBg,
            surface = surf,
            onSurface = onBg,
            surfaceVariant = surfVariant,
            onSurfaceVariant = onSurfVariant,
            surfaceTint = accentPrimary,
            inverseSurface = onBg,
            inverseOnSurface = bg,
            outline = onSurfVariant.copy(alpha = 0.55f),
            outlineVariant = onSurfVariant.copy(alpha = 0.25f),
            surfaceContainerLowest = bg,
            surfaceContainerLow = surf,
            surfaceContainer = surf,
            surfaceContainerHigh = surfVariant,
            surfaceContainerHighest = surfVariant,
        )
    }

    companion object {
        fun fromId(id: String?): AppPalette =
            entries.firstOrNull { it.id == id } ?: CELADON
    }
}

/**
 * 这个底色上应该用深色文字还是浅色文字。
 *
 * 用 WCAG 对比度算，而不是简单看亮度阈值 —— 中灰（#808080）这种
 * 半深不浅的颜色，黑字的对比度其实更高。
 */
fun Color.isLightBackground(): Boolean {
    val lum = luminance()
    val whiteContrast = 1.05f / (lum + 0.05f)
    val blackContrast = (lum + 0.05f) / 0.05f
    return blackContrast >= whiteContrast
}

/**
 * 浅色底上的强调色：保留色相，把饱和度拉到至少 0.6、明度压到 0.44。
 *
 * 直接和黑色混合会把粉彩（薄荷/铜/淡紫）混成灰调，
 * 选中图标和普通灰色图标几乎分不开；按 HSV 调整能保住「有颜色」的感觉。
 */
private fun Color.vividOnLight(): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.60f)
    hsv[2] = hsv[2].coerceAtMost(0.44f)
    return Color(AndroidColor.HSVToColor(hsv))
}

/** 取色器里用的十六进制文本，不带 alpha */
fun Color.toHexString(): String =
    String.format("#%06X", toArgb() and 0xFFFFFF)

/** 解析 #RRGGBB / RRGGBB；格式不对返回 null */
fun parseHexColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
