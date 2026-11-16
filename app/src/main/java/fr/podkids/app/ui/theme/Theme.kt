package fr.podkids.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.podkids.app.data.settings.DarkMode
import fr.podkids.app.data.settings.UiSettings

/** Couleurs de base d'un thème ; le reste de la palette Material 3 en est dérivé. */
data class Palette(val primary: Color, val secondary: Color, val tertiary: Color)

private val OceanPalette = Palette(Color(0xFF1565C0), Color(0xFF00838F), Color(0xFFEF6C00))

private fun lightScheme(p: Palette): ColorScheme {
    val white = Color.White
    val ink = Color(0xFF1B1B1F)
    return lightColorScheme(
        primary = p.primary,
        onPrimary = white,
        primaryContainer = lerp(p.primary, white, 0.82f),
        onPrimaryContainer = lerp(p.primary, Color.Black, 0.6f),
        secondary = p.secondary,
        onSecondary = white,
        secondaryContainer = lerp(p.secondary, white, 0.82f),
        onSecondaryContainer = lerp(p.secondary, Color.Black, 0.6f),
        tertiary = p.tertiary,
        onTertiary = white,
        tertiaryContainer = lerp(p.tertiary, white, 0.82f),
        onTertiaryContainer = lerp(p.tertiary, Color.Black, 0.6f),
        background = lerp(p.primary, white, 0.95f),
        onBackground = ink,
        surface = lerp(p.primary, white, 0.95f),
        onSurface = ink,
        surfaceVariant = lerp(p.primary, white, 0.86f),
        onSurfaceVariant = Color(0xFF45464F),
        surfaceContainerLowest = white,
        surfaceContainerLow = lerp(p.primary, white, 0.93f),
        surfaceContainer = lerp(p.primary, white, 0.91f),
        surfaceContainerHigh = lerp(p.primary, white, 0.88f),
        surfaceContainerHighest = lerp(p.primary, white, 0.85f),
        outline = Color(0xFF767680),
        outlineVariant = lerp(p.primary, white, 0.7f),
    )
}

private fun darkScheme(p: Palette): ColorScheme {
    val base = Color(0xFF121316)
    val paper = Color(0xFFE5E1E6)
    fun tone(color: Color) = lerp(color, Color.White, 0.55f)
    return darkColorScheme(
        primary = tone(p.primary),
        onPrimary = lerp(p.primary, Color.Black, 0.65f),
        primaryContainer = lerp(p.primary, Color.Black, 0.35f),
        onPrimaryContainer = lerp(p.primary, Color.White, 0.85f),
        secondary = tone(p.secondary),
        onSecondary = lerp(p.secondary, Color.Black, 0.65f),
        secondaryContainer = lerp(p.secondary, Color.Black, 0.4f),
        onSecondaryContainer = lerp(p.secondary, Color.White, 0.85f),
        tertiary = tone(p.tertiary),
        onTertiary = lerp(p.tertiary, Color.Black, 0.65f),
        tertiaryContainer = lerp(p.tertiary, Color.Black, 0.4f),
        onTertiaryContainer = lerp(p.tertiary, Color.White, 0.85f),
        background = lerp(base, p.primary, 0.06f),
        onBackground = paper,
        surface = lerp(base, p.primary, 0.06f),
        onSurface = paper,
        surfaceVariant = lerp(base, p.primary, 0.22f),
        onSurfaceVariant = Color(0xFFC6C5D0),
        surfaceContainerLowest = lerp(base, Color.Black, 0.3f),
        surfaceContainerLow = lerp(base, p.primary, 0.09f),
        surfaceContainer = lerp(base, p.primary, 0.12f),
        surfaceContainerHigh = lerp(base, p.primary, 0.16f),
        surfaceContainerHighest = lerp(base, p.primary, 0.2f),
        outline = Color(0xFF90909A),
        outlineVariant = lerp(base, p.primary, 0.35f),
    )
}

/** Coins très arrondis : un langage visuel doux, adapté aux enfants. */
private val PodkidsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val PodkidsTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    )
}

@Composable
fun PodkidsTheme(settings: UiSettings, content: @Composable () -> Unit) {
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val palette = OceanPalette
    val colors = if (dark) darkScheme(palette) else lightScheme(palette)

    MaterialTheme(colorScheme = colors, shapes = PodkidsShapes, typography = PodkidsTypography) {
        content()
    }
}

/** Vrai si le thème effectif est sombre (utile pour les barres système). */
@Composable
fun isDarkTheme(settings: UiSettings): Boolean = when (settings.darkMode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}
