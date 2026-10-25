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
    excellent = Color(0xFF2E7D32),
    good = Color(0xFF558B2F),
    fair = Color(0xFF9A6400),
    poor = Color(0xFFC75000),
    bad = Color(0xFFB3261E),
    warningSurface = Color(0xFFFFF3D6),
)

private val DarkExtras = EcoRotaExtraColors(
    excellent = Color(0xFF7FD48A),
    good = Color(0xFFB5D96B),
    fair = Color(0xFFFFC46B),
    poor = Color(0xFFFF9E6B),
    bad = Color(0xFFFF8A80),
    warningSurface = Color(0xFF3A2F14),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtras }

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

/** Score bands. Thresholds are inclusive lower bounds. */
enum class ScoreBand(val minScore: Int) {
    BAD(0),
    POOR(40),
    FAIR(55),
    GOOD(70),
    EXCELLENT(85),
    ;

    companion object {
        fun from(score: Int?): ScoreBand? = score?.let { value ->
            entries.last { value >= it.minScore }
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
