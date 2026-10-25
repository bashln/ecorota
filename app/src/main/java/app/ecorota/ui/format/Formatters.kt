package app.ecorota.ui.format

import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.FuelUnits
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Pure presentation helpers. Every function takes the [Locale] it should use so
 * unit tests can pin the output and the app can follow the device locale.
 */
object Formatters {

    /** "850 m" below one kilometer, "12.3 km" above it. */
    fun distance(meters: Double, locale: Locale): String {
        val safe = meters.coerceAtLeast(0.0)
        return if (safe < 1_000.0) {
            String.format(locale, "%.0f m", safe)
        } else {
            String.format(locale, "%.1f km", safe / 1_000.0)
        }
    }

    /** "2 h 05 min", "12 min 30 s" or "45 s". */
    fun duration(milliseconds: Long, locale: Locale): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> String.format(locale, "%d h %02d min", hours, minutes)
            minutes > 0 -> String.format(locale, "%d min %02d s", minutes, seconds)
            else -> String.format(locale, "%d s", seconds)
        }
    }

    fun speed(kmh: Double, locale: Locale): String =
        String.format(locale, "%.0f km/h", kmh.coerceAtLeast(0.0))

    /** Two decimals keep small avoidable amounts visible. */
    fun liters(value: Double, locale: Locale): String {
        val safe = if (abs(value) < 0.005) 0.0 else value
        return String.format(locale, "%.2f L", safe)
    }

    fun money(amount: Double, currencyCode: String, locale: Locale): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
            ?: Currency.getInstance("BRL")
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        // Some locales separate the symbol with a non-breaking space; normalize
        // so tests and screen readers see a predictable string.
        return format.format(amount).replace('\u00A0', ' ').replace('\u202F', ' ')
    }

    /** Display value in the unit the user prefers, e.g. "8.5" or "11.8". */
    fun consumptionValue(
        litersPer100Km: Double,
        unit: ConsumptionUnit,
        locale: Locale,
    ): String {
        val value = FuelUnits.fromLitersPer100Km(litersPer100Km, unit)
        return String.format(locale, "%.1f", value)
    }

    /** Value plus unit, e.g. "8.5 L/100 km" or "11.8 km/L". */
    fun consumption(
        litersPer100Km: Double,
        unit: ConsumptionUnit,
        locale: Locale,
    ): String = "${consumptionValue(litersPer100Km, unit, locale)} ${consumptionUnitLabel(unit)}"

    fun consumptionUnitLabel(unit: ConsumptionUnit): String = when (unit) {
        ConsumptionUnit.LITERS_PER_100_KM -> "L/100 km"
        ConsumptionUnit.KILOMETERS_PER_LITER -> "km/L"
    }

    /** Fraction (0..1) or percentage (0..100) rendered without decimals. */
    fun percent(value: Double, locale: Locale): String =
        String.format(locale, "%.0f%%", value.coerceIn(0.0, 100.0))

    fun points(value: Double, locale: Locale): String =
        String.format(locale, "%.1f", value.coerceAtLeast(0.0))

    fun integer(value: Int, locale: Locale): String = String.format(locale, "%d", value)

    /**
     * Parses user input that may use a comma or a dot as the decimal mark.
     * Returns null for blank or non-numeric input; callers validate the range.
     */
    fun parseDecimal(raw: String): Double? {
        val normalized = raw.trim()
            .replace(" ", "")
            .replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value.isFinite()) value else null
    }
}
