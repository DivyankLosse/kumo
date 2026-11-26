package com.ascend.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.R
import com.ascend.app.data.prefs.ThemeMode
import com.ascend.app.domain.Category

// Dawn Summit palette. Pastels are the user's Morning Breeze set.
object Palette {
    val Ink = Color(0xFF0D1120)
    val Ink1 = Color(0xFF131829)
    val Ink2 = Color(0xFF1A2034)
    val Ink3 = Color(0xFF222944)
    val Ink4 = Color(0xFF2A3150)
    val Cream = Color(0xFFFFF7D6)
    val Cream1 = Color(0xFFFFF2C4)
    val Cream2 = Color(0xFFF8EBBE)
    val Cream3 = Color(0xFFF1E3B4)
    val Cream4 = Color(0xFFEADFB4)
    val Butter = Color(0xFFFFE08A)
    val ButterPale = Color(0xFFFFF2B2)
    val Sky = Color(0xFFA8C6E7)
    val DeepSky = Color(0xFF7FA8D6)
    val Blush = Color(0xFFF4B6A6)
    val Sage = Color(0xFFB5D8B0)
    val Lilac = Color(0xFFC9B8F0)
    val Peach = Color(0xFFFFC99A)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Butter, onPrimary = Color(0xFF2B2200),
    primaryContainer = Color(0xFF4A3D10), onPrimaryContainer = Palette.ButterPale,
    secondary = Palette.Sky, onSecondary = Color(0xFF0F2236),
    secondaryContainer = Color(0xFF233A55), onSecondaryContainer = Color(0xFFD6E6F7),
    tertiary = Palette.DeepSky, onTertiary = Color(0xFF07213E),
    tertiaryContainer = Color(0xFF1E3A5C), onTertiaryContainer = Color(0xFFD3E4FA),
    background = Palette.Ink, onBackground = Color(0xFFEDEFF7),
    surface = Palette.Ink, onSurface = Color(0xFFEDEFF7),
    surfaceVariant = Palette.Ink3, onSurfaceVariant = Color(0xFFB9C3DA),
    surfaceContainerLowest = Color(0xFF0A0D19), surfaceContainerLow = Palette.Ink1,
    surfaceContainer = Palette.Ink2, surfaceContainerHigh = Palette.Ink3, surfaceContainerHighest = Palette.Ink4,
    outline = Color(0xFF5A6382), outlineVariant = Color(0xFF2F3756),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    inverseSurface = Palette.Cream, inverseOnSurface = Palette.Ink, inversePrimary = Color(0xFF8A6A00),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A5D00), onPrimary = Color.White,
    primaryContainer = Palette.Butter, onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF3F6A9A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F7), onSecondaryContainer = Color(0xFF0D2847),
    tertiary = Color(0xFF2F5E92), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE0F6), onTertiaryContainer = Color(0xFF0A2547),
    background = Palette.Cream, onBackground = Color(0xFF1C1B16),
    surface = Palette.Cream, onSurface = Color(0xFF1C1B16),
    surfaceVariant = Palette.Cream3, onSurfaceVariant = Color(0xFF4F4A3A),
    surfaceContainerLowest = Color(0xFFFFFCEB), surfaceContainerLow = Palette.Cream1,
    surfaceContainer = Palette.Cream2, surfaceContainerHigh = Palette.Cream3, surfaceContainerHighest = Palette.Cream4,
    outline = Color(0xFF857D66), outlineVariant = Color(0xFFD6CBA6),
    error = Color(0xFFB3261E), onError = Color.White,
    inverseSurface = Palette.Ink, inverseOnSurface = Palette.Cream, inversePrimary = Palette.Butter,
)

/** Domain hues. Dark uses the pastel; light uses a deeper tone that passes contrast on cream. */
@Immutable
data class CategoryColors(val dark: Boolean) {
    fun of(c: Category): Color = when (c) {
        Category.CAREER -> if (dark) Palette.DeepSky else Color(0xFF2F5E92)
        Category.ACADEMICS -> if (dark) Palette.Butter else Color(0xFF7A5D00)
        Category.FITNESS -> if (dark) Palette.Blush else Color(0xFFA4513E)
        Category.HEALTH -> if (dark) Palette.Sage else Color(0xFF3F6B3A)
        Category.POSTURE -> if (dark) Color(0xFF9EDBD0) else Color(0xFF2D6B61)
        Category.GROOMING -> if (dark) Palette.Peach else Color(0xFF8C4F1C)
        Category.COMMUNICATION -> if (dark) Palette.Lilac else Color(0xFF5B4596)
        Category.SOCIAL -> if (dark) Color(0xFFF2B8D8) else Color(0xFF8E3A68)
        Category.COLLEGE -> if (dark) Palette.Sky else Color(0xFF3F6A9A)
        Category.COMMUTE -> if (dark) Color(0xFF8C95AE) else Color(0xFF6A6553)
        Category.PERSONAL -> if (dark) Color(0xFFE6D3B3) else Color(0xFF6B5634)
    }
}

val LocalCategoryColors = staticCompositionLocalOf { CategoryColors(true) }
val LocalIsDark = staticCompositionLocalOf { true }
val LocalReduceMotion = staticCompositionLocalOf { false }

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun gf(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) },
)

val DisplayFamily = gf("Bricolage Grotesque", FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
val BodyFamily = gf("Figtree", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private val Tnum = "tnum"

val AscendTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = Tnum),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = Tnum),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = Tnum),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = Tnum),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = Tnum),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = Tnum),
)

val AscendShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Motion tokens (see DESIGN.md). Critically damped springs for everyday UI (Apple fluid-interface
 * guidance: no bounce on things seen many times a day); bounce is reserved for celebrations.
 * Reduce-motion collapses every spec to an instant snap.
 */
object Motion {
    fun <T> snappy(reduce: Boolean): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = 1f, stiffness = 500f)
    fun <T> gentle(reduce: Boolean): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = 1f, stiffness = 200f)
    fun <T> bouncy(reduce: Boolean): AnimationSpec<T> = if (reduce) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

@Composable
fun AscendTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalCategoryColors provides CategoryColors(dark),
        LocalIsDark provides dark,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AscendTypography,
            shapes = AscendShapes,
            content = content,
        )
    }
}
