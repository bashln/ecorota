package app.ecorota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
    primary = Color(0xFF245EA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF102D50),
    secondary = Color(0xFF566173),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E7EF),
    onSecondaryContainer = Color(0xFF182231),
    tertiary = Color(0xFF555F80),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE5FF),
    onTertiaryContainer = Color(0xFF141F3C),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191D25),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191D25),
    surfaceVariant = Color(0xFFE2E6ED),
    onSurfaceVariant = Color(0xFF444C59),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFEBEFF5),
    surfaceContainerHigh = Color(0xFFE5EAF2),
    surfaceContainerHighest = Color(0xFFDFE5EE),
    outline = Color(0xFF737C89),
    outlineVariant = Color(0xFFC4CBD6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CBFF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF224775),
    onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = Color(0xFFBDC7D8),
    onSecondary = Color(0xFF273140),
    secondaryContainer = Color(0xFF3D4757),
    onSecondaryContainer = Color(0xFFE2E7EF),
    tertiary = Color(0xFFBBC6EA),
    onTertiary = Color(0xFF263151),
    tertiaryContainer = Color(0xFF3D4868),
    onTertiaryContainer = Color(0xFFDEE5FF),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE1E5ED),
    surface = Color(0xFF101319),
    onSurface = Color(0xFFE1E5ED),
    surfaceVariant = Color(0xFF444C59),
    onSurfaceVariant = Color(0xFFC4CBD6),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF181C24),
    surfaceContainer = Color(0xFF1D222B),
    surfaceContainerHigh = Color(0xFF272D37),
    surfaceContainerHighest = Color(0xFF323945),
    outline = Color(0xFF8E98A7),
    outlineVariant = Color(0xFF444C59),
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
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
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
