package com.amit.pageone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amit.pageone.R
import com.amit.pageone.data.AppSettings
import com.amit.pageone.data.PaperLook
import com.amit.pageone.data.ThemeMode

val ScriptFont = FontFamily(
    Font(R.font.courier_prime, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
    Font(R.font.courier_prime_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.courier_prime_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(ExperimentalTextApi::class)
val UiFont = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = UiFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = UiFont),
    bodySmall = base.bodySmall.copy(fontFamily = UiFont),
    labelLarge = base.labelLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Medium),
)

/** Colours for the screenplay "paper" and the desk around it. */
@Immutable
data class EditorPalette(
    val desk: Color,
    val paper: Color,
    val ink: Color,
    val faint: Color,
    val gutter: Color,
    val note: Color,
    val noteInk: Color,
    val pageEdge: Color,
)

val LocalEditorPalette = staticCompositionLocalOf {
    EditorPalette(Color.DarkGray, Color.White, Color.Black, Color.Gray, Color.Gray, Color.Yellow, Color.Black, Color.LightGray)
}

@Composable
fun isDark(settings: AppSettings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
}

/** [c] with its hue turned by [degrees] — for a second brand colour that always harmonises. */
fun rotateHue(c: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The "studio" look: deep blue-black layers (like a grading suite) in the dark, warm ivory
 * stock in the light, with the accent used sparingly as light — glows, rings, edges.
 */
private fun schemeFor(accent0: Color, dark: Boolean, black: Boolean): ColorScheme {
    // A muted partner colour (never a loud complement), and accents that hold contrast on ink and on ivory.
    val second = lerp(rotateHue(accent0, 155f), Color(0xFF8E8E8E), 0.5f)
    val accent = if (dark && accent0.luminance() < 0.2f) lerp(accent0, Color.White, 0.38f) else accent0
    val onAccent = if (accent.luminance() > 0.42f) Color(0xFF16120C) else Color.White
    return if (dark) {
        val bg = if (black) Color(0xFF000000) else Color(0xFF0A0B0F)
        val surface = if (black) Color(0xFF07080A) else Color(0xFF101218)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(surface, accent, 0.20f),
            onPrimaryContainer = lerp(Color.White, accent, 0.30f),
            secondary = lerp(accent, Color.White, 0.30f),
            onSecondary = Color(0xFF111111),
            secondaryContainer = Color(0xFF232733),
            onSecondaryContainer = Color(0xFFE9E7F0),
            tertiary = lerp(second, Color.White, 0.25f),
            onTertiary = Color(0xFF0B0B0F),
            tertiaryContainer = lerp(surface, second, 0.24f),
            onTertiaryContainer = lerp(Color.White, second, 0.25f),
            error = Color(0xFFFF6B6B),
            errorContainer = Color(0xFF3A1518),
            onErrorContainer = Color(0xFFFFD9D9),
            background = bg,
            onBackground = Color(0xFFEDEBF2),
            surface = surface,
            onSurface = Color(0xFFEDEBF2),
            surfaceVariant = Color(0xFF1E222C),
            onSurfaceVariant = Color(0xFFA3A7B5),
            surfaceTint = accent,
            outline = Color(0xFF353A47),
            outlineVariant = Color(0xFF242833),
            surfaceContainerLowest = bg,
            surfaceContainerLow = if (black) Color(0xFF060709) else Color(0xFF0E1015),
            surfaceContainer = if (black) Color(0xFF0D0E12) else Color(0xFF151821),
            surfaceContainerHigh = if (black) Color(0xFF13151A) else Color(0xFF1B1F29),
            surfaceContainerHighest = if (black) Color(0xFF1A1C22) else Color(0xFF232733),
            inverseSurface = Color(0xFFEDEBF2),
            inverseOnSurface = Color(0xFF101218),
        )
    } else {
        lightColorScheme(
            primary = if (accent.luminance() > 0.3f) lerp(accent, Color.Black, 0.22f) else accent,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, accent, 0.20f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.62f),
            secondary = lerp(accent, Color.Black, 0.38f),
            secondaryContainer = Color(0xFFEFE9DF),
            onSecondaryContainer = Color(0xFF28231C),
            tertiary = lerp(second, Color.Black, 0.30f),
            tertiaryContainer = lerp(Color.White, second, 0.16f),
            onTertiaryContainer = lerp(second, Color.Black, 0.65f),
            background = Color(0xFFF7F4EE),
            onBackground = Color(0xFF1B1A18),
            surface = Color(0xFFFCFAF6),
            onSurface = Color(0xFF1B1A18),
            surfaceVariant = Color(0xFFEDE8DF),
            onSurfaceVariant = Color(0xFF67615A),
            outline = Color(0xFFD5CEC2),
            outlineVariant = Color(0xFFE6E0D5),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF9F6F1),
            surfaceContainer = Color(0xFFF3EFE8),
            surfaceContainerHigh = Color(0xFFEDE9E1),
            surfaceContainerHighest = Color(0xFFE7E2D9),
        )
    }
}

/** Brand colours beyond Material's scheme: the accent pair, glows and gradient edges. */
@Immutable
data class Brand(
    val accent: Color,
    val accent2: Color,
    val glow: Color,
    val dark: Boolean,
    val success: Color,
    val warning: Color,
) {
    /** Accent → its harmonic partner, for hero numbers, rings and buttons. */
    val hero: Brush get() = Brush.linearGradient(listOf(accent, accent2))
    /** A hairline edge that catches the light at the top-left, for premium cards. */
    val edge: Brush get() = Brush.linearGradient(listOf(accent.copy(alpha = if (dark) 0.55f else 0.45f), Color.Transparent, accent2.copy(alpha = 0.25f)))
}

val LocalBrand = staticCompositionLocalOf {
    Brand(Color(0xFFE7B35A), Color(0xFF5A8FE7), Color(0x33E7B35A), true, Color(0xFF3FBF7F), Color(0xFFFFB547))
}

@Composable
fun PageOneTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val accent = Color(settings.accent.argb)
    val ctx = LocalContext.current
    val scheme = if (settings.dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val d = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (dark && settings.theme == ThemeMode.BLACK) d.copy(background = Color.Black, surface = Color(0xFF0A0A0C)) else d
    } else schemeFor(accent, dark, settings.theme == ThemeMode.BLACK)

    val palette = when {
        settings.paperLook == PaperLook.WHITE || (!dark && settings.paperLook == PaperLook.MATCH) -> EditorPalette(
            desk = if (dark) scheme.background else Color(0xFFE7E3DC),
            paper = Color(0xFFFFFFFF), ink = Color(0xFF151515), faint = Color(0xFF9A968F),
            gutter = Color(0xFFB1ACA4), note = Color(0xFFFFF4C2), noteInk = Color(0xFF5A4A12),
            pageEdge = Color(0x22000000),
        )
        settings.paperLook == PaperLook.SEPIA -> EditorPalette(
            desk = if (dark) scheme.background else Color(0xFFE2D8C3),
            paper = Color(0xFFF5EDDA), ink = Color(0xFF3A2E22), faint = Color(0xFF9C8D78),
            gutter = Color(0xFFA89878), note = Color(0xFFFFE9A8), noteInk = Color(0xFF5A4A12),
            pageEdge = Color(0x33000000),
        )
        else -> EditorPalette(
            desk = scheme.background,
            paper = if (settings.theme == ThemeMode.BLACK) Color(0xFF0B0B0D) else Color(0xFF161922),
            ink = Color(0xFFECE9E2), faint = Color(0xFF6C7080),
            gutter = Color(0xFF5C6070), note = Color(0xFF3A3420), noteInk = Color(0xFFF0DD9A),
            pageEdge = Color(0x33FFFFFF),
        )
    }

    val brand = Brand(
        accent = scheme.primary,
        accent2 = lerp(rotateHue(scheme.primary, 28f), scheme.primary, 0.45f),
        glow = scheme.primary.copy(alpha = if (dark) 0.20f else 0.14f),
        dark = dark,
        success = if (dark) Color(0xFF45D19A) else Color(0xFF1E9E66),
        warning = if (dark) Color(0xFFFFB547) else Color(0xFFC77A00),
    )

    CompositionLocalProvider(LocalEditorPalette provides palette, LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // Screens drawn straight onto the background (no Surface) must still get light
            // text and icons in dark mode — Compose's root default is black.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

val ScriptTextStyle = TextStyle(fontFamily = ScriptFont)
