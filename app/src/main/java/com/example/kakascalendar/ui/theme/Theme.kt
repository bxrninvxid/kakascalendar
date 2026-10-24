package com.example.kakascalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Accent(val label: String, val primary: Color, val secondary: Color, val tertiary: Color) {
    CARAMEL("Caramel", Color(0xFF9A5B34), Color(0xFFE39B5B), Color(0xFFF2C66D)),
    MINT("Mint", Color(0xFF23806A), Color(0xFF5FC9A0), Color(0xFFB6DE6A)),
    GRAPE("Grape", Color(0xFF6A4CC2), Color(0xFFB08AF2), Color(0xFFF08AC6)),
    OCEAN("Ocean", Color(0xFF2465B0), Color(0xFF55BFE6), Color(0xFF86DEC4)),
    CHERRY("Cherry", Color(0xFFC0364A), Color(0xFFFF8A65), Color(0xFFFFC46B)),
    HOMELANDER("Homelander", Color(0xFF1D3B8F), Color(0xFFD62839), Color(0xFFF2C14E));

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { CARAMEL }
    }
}

/** Colors for the user-picked "Custom" accent. */
data class AccentColors(val primary: Color, val secondary: Color, val tertiary: Color)

fun Accent.colors() = AccentColors(primary, secondary, tertiary)

fun customAccent(hue: Float): AccentColors {
    val h = ((hue % 360f) + 360f) % 360f
    return AccentColors(
        primary = Color.hsv(h, 0.70f, 0.62f),
        secondary = Color.hsv((h + 28f) % 360f, 0.58f, 0.88f),
        tertiary = Color.hsv((h + 56f) % 360f, 0.50f, 0.96f)
    )
}

fun accentColors(index: Int, hue: Float): AccentColors =
    if (index == com.example.kakascalendar.data.CUSTOM_ACCENT) customAccent(hue) else Accent.of(index).colors()

enum class AppFont(val label: String) {
    DEFAULT("Default"), CONDENSED("Condensed"), SERIF("Classic"), MONO("Mono"), PLAYFUL("Playful");

    fun family(): FontFamily = when (this) {
        DEFAULT -> FontFamily.Default
        CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
        SERIF -> FontFamily.Serif
        MONO -> FontFamily.Monospace
        PLAYFUL -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
    }

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { DEFAULT }
    }
}

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

private fun schemeFor(a: AccentColors, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color(0xFF0E0B0A)
    return if (!dark) {
        val bg = mix(Color(0xFFFFFBF7), a.primary, 0.035f)
        lightColorScheme(
            primary = a.primary, onPrimary = white,
            primaryContainer = mix(a.primary, white, 0.82f), onPrimaryContainer = mix(a.primary, black, 0.55f),
            secondary = a.secondary, onSecondary = white,
            secondaryContainer = mix(a.secondary, white, 0.78f), onSecondaryContainer = mix(a.secondary, black, 0.6f),
            tertiary = a.tertiary, onTertiary = black,
            tertiaryContainer = mix(a.tertiary, white, 0.75f), onTertiaryContainer = mix(a.tertiary, black, 0.65f),
            background = bg, onBackground = Color(0xFF22190F),
            surface = bg, onSurface = Color(0xFF22190F),
            surfaceVariant = mix(white, a.primary, 0.07f), onSurfaceVariant = mix(Color(0xFF5F5650), a.primary, 0.15f),
            surfaceContainerLowest = white,
            surfaceContainerLow = mix(white, a.primary, 0.03f),
            surfaceContainer = mix(white, a.primary, 0.05f),
            surfaceContainerHigh = mix(white, a.primary, 0.08f),
            surfaceContainerHighest = mix(white, a.primary, 0.11f),
            surfaceBright = white, surfaceDim = mix(white, a.primary, 0.12f),
            outline = mix(Color(0xFF8C837D), a.primary, 0.2f),
            outlineVariant = mix(Color(0xFFDAD2CC), a.primary, 0.1f),
            inverseSurface = Color(0xFF362E29), inverseOnSurface = Color(0xFFFBEFE7),
            inversePrimary = mix(a.primary, white, 0.45f)
        )
    } else {
        val p = mix(a.primary, white, 0.38f)
        val base = mix(Color(0xFF131011), a.primary, 0.06f)
        darkColorScheme(
            primary = p, onPrimary = mix(a.primary, black, 0.7f),
            primaryContainer = mix(a.primary, black, 0.45f), onPrimaryContainer = mix(a.primary, white, 0.82f),
            secondary = mix(a.secondary, white, 0.15f), onSecondary = black,
            secondaryContainer = mix(a.secondary, black, 0.55f), onSecondaryContainer = mix(a.secondary, white, 0.8f),
            tertiary = a.tertiary, onTertiary = black,
            tertiaryContainer = mix(a.tertiary, black, 0.6f), onTertiaryContainer = mix(a.tertiary, white, 0.8f),
            background = base, onBackground = Color(0xFFF2E8E1),
            surface = base, onSurface = Color(0xFFF2E8E1),
            surfaceVariant = mix(Color(0xFF241F1C), a.primary, 0.12f), onSurfaceVariant = Color(0xFFCFC3BA),
            surfaceContainerLowest = mix(Color(0xFF0C0A09), a.primary, 0.04f),
            surfaceContainerLow = mix(Color(0xFF1A1614), a.primary, 0.07f),
            surfaceContainer = mix(Color(0xFF1F1A18), a.primary, 0.09f),
            surfaceContainerHigh = mix(Color(0xFF29231F), a.primary, 0.11f),
            surfaceContainerHighest = mix(Color(0xFF342D29), a.primary, 0.13f),
            surfaceBright = mix(Color(0xFF3B3430), a.primary, 0.1f), surfaceDim = base,
            outline = Color(0xFF9A8E86), outlineVariant = Color(0xFF4B433E),
            inverseSurface = Color(0xFFF2E8E1), inverseOnSurface = Color(0xFF2B2420),
            inversePrimary = a.primary
        )
    }
}

private val base = Typography()

fun appTypography(family: FontFamily): Typography {
    fun TextStyle.f(weight: FontWeight? = null, spacing: Float? = null) =
        copy(fontFamily = family, fontWeight = weight ?: fontWeight, letterSpacing = spacing?.sp ?: letterSpacing)
    return Typography(
        displayLarge = base.displayLarge.f(FontWeight.Black, -1f),
        displayMedium = base.displayMedium.f(FontWeight.Black, -1f),
        displaySmall = base.displaySmall.f(FontWeight.ExtraBold, -0.5f),
        headlineLarge = base.headlineLarge.f(FontWeight.ExtraBold, -0.5f),
        headlineMedium = base.headlineMedium.f(FontWeight.ExtraBold, -0.4f),
        headlineSmall = base.headlineSmall.f(FontWeight.Bold, -0.3f),
        titleLarge = base.titleLarge.f(FontWeight.ExtraBold, -0.3f),
        titleMedium = base.titleMedium.f(FontWeight.Bold),
        titleSmall = base.titleSmall.f(FontWeight.Bold),
        labelLarge = base.labelLarge.f(FontWeight.SemiBold),
        labelMedium = base.labelMedium.f(FontWeight.SemiBold),
        labelSmall = base.labelSmall.f(FontWeight.Medium),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f()
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun isAppDark(mode: Int): Boolean = when (mode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun KakaTheme(
    mode: Int, accent: Int, dynamic: Boolean, hue: Float = 28f, font: Int = 0,
    content: @Composable () -> Unit
) {
    val dark = isAppDark(mode)
    val ctx = LocalContext.current
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else schemeFor(accentColors(accent, hue), dark)
    val typography = remember(font) { appTypography(AppFont.of(font).family()) }
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = AppShapes, content = content)
}
