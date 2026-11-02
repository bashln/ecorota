package app.ecorota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.ecorota.core.model.ThemeMode

/**
 * Brand palette. Dynamic color is intentionally not used: the score bands carry
 * meaning, and a device accent could make "good" and "bad" look alike.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F1B2),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F0F),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF191C18),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF191C18),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6EE),
    surfaceContainer = Color(0xFFEDF1E8),
    surfaceContainerHigh = Color(0xFFE7ECE2),
    surfaceContainerHighest = Color(0xFFE1E6DC),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D497),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF11501A),
    onPrimaryContainer = Color(0xFFB4F1B2),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00373B),
    tertiaryContainer = Color(0xFF1F4E52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E4DC),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E4DC),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262C26),
    surfaceContainerHighest = Color(0xFF313731),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Immutable
data class EcoRotaExtraColors(
    val excellent: Color,
    val good: Color,
    val fair: Color,
    val poor: Color,
    val bad: Color,
    val warningSurface: Color,
)

private val LightExtras = EcoRotaExtraColors(
    excellent = Color(0xFF16611C),
    good = Color(0xFF2F5C0E),
    fair = Color(0xFF734700),
    poor = Color(0xFF913500),
    bad = Color(0xFF961912),
    warningSurface = Color(0xFFFFF3D6),
)

private val DarkExtras = EcoRotaExtraColors(
    excellent = Color(0xFF88DC93),
    good = Color(0xFFBAE070),
    fair = Color(0xFFFFC875),
    poor = Color(0xFFFFAF85),
    bad = Color(0xFFFFA69E),
    warningSurface = Color(0xFF3A2F14),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtras }
private val LocalIsDarkTheme = staticCompositionLocalOf { false }

val EcoRotaTypography = Typography().copy(
    displayLarge = TextStyle(
        fontSize = 64.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun EcoRotaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extras = if (dark) DarkExtras else LightExtras
    CompositionLocalProvider(
        LocalExtraColors provides extras,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = EcoRotaTypography,
            content = content,
        )
    }
}

val extraColors: EcoRotaExtraColors
    @Composable get() = LocalExtraColors.current

val isEcoRotaDarkTheme: Boolean
    @Composable get() = LocalIsDarkTheme.current

/** Score bands. Thresholds are inclusive lower bounds. */
enum class ScoreBand(val minScore: Int) {
    BAD(0),
    POOR(40),
    FAIR(55),
    GOOD(70),
    EXCELLENT(85),
    ;

    companion object {
        fun from(score: Int?): ScoreBand? = score?.let { raw ->
            val clamped = raw.coerceAtLeast(0)
            entries.last { clamped >= it.minScore }
        }
    }
}

@Composable
fun ScoreBand.color(): Color = when (this) {
    ScoreBand.EXCELLENT -> extraColors.excellent
    ScoreBand.GOOD -> extraColors.good
    ScoreBand.FAIR -> extraColors.fair
    ScoreBand.POOR -> extraColors.poor
    ScoreBand.BAD -> extraColors.bad
}

@Composable
fun scoreColor(score: Int?): Color {
    val band = ScoreBand.from(score) ?: return MaterialTheme.colorScheme.outline
    return band.color()
}
