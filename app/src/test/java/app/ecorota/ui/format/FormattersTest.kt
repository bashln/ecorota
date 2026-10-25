package app.ecorota.ui.format

import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.SpeedUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    private val en = Locale.US
    private val pt = Locale.forLanguageTag("pt-BR")

    @Test
    fun distanceBelowOneKilometerUsesMeters() {
        assertEquals("850 m", Formatters.distance(850.0, en))
        assertEquals("850 m", Formatters.distance(850.0, pt))
    }

    @Test
    fun distanceAboveOneKilometerUsesOneDecimal() {
        assertEquals("12.3 km", Formatters.distance(12_345.0, en))
        assertEquals("12,3 km", Formatters.distance(12_345.0, pt))
        assertEquals("1.0 km", Formatters.distance(1_000.0, en))
    }

    @Test
    fun durationSplitsHoursMinutesAndSeconds() {
        assertEquals("1 h 05 min", Formatters.duration(3_900_000L, en))
        assertEquals("12 min 30 s", Formatters.duration(750_000L, en))
        assertEquals("45 s", Formatters.duration(45_000L, en))
        assertEquals("0 s", Formatters.duration(0L, en))
        assertEquals("1 h 05 min", Formatters.duration(3_900_000L, pt))
        assertEquals("12 min 30 s", Formatters.duration(750_000L, pt))
    }

    @Test
    fun speedHasNoDecimalsAndConvertsUnits() {
        assertEquals("88 km/h", Formatters.speed(87.6, SpeedUnit.KILOMETERS_PER_HOUR, en))
        assertEquals("88 km/h", Formatters.speed(87.6, SpeedUnit.KILOMETERS_PER_HOUR, pt))
        assertEquals("54 mph", Formatters.speed(87.6, SpeedUnit.MILES_PER_HOUR, en))
        assertEquals("54 mph", Formatters.speed(87.6, SpeedUnit.MILES_PER_HOUR, pt))
        assertEquals("0 km/h", Formatters.speed(-3.0, SpeedUnit.KILOMETERS_PER_HOUR, en))
    }

    @Test
    fun litersUseTwoDecimals() {
        assertEquals("1.23 L", Formatters.liters(1.234, en))
        assertEquals("1,23 L", Formatters.liters(1.234, pt))
    }

    @Test
    fun moneyFollowsLocaleAndCurrencyCode() {
        assertEquals("R$1,234.50", Formatters.money(1_234.5, "BRL", en))
        assertEquals("R$ 1.234,50", Formatters.money(1_234.5, "BRL", pt))
        assertEquals("$12.30", Formatters.money(12.3, "USD", en))
        assertEquals("US$ 12,30", Formatters.money(12.3, "USD", pt))
    }

    @Test
    fun moneyFallsBackToBrlForUnknownCode() {
        assertEquals("R$9.00", Formatters.money(9.0, "NOT_A_CODE", en))
    }

    @Test
    fun consumptionConvertsLitersPer100KmToKilometersPerLiter() {
        assertEquals(
            "8.5 L/100 km",
            Formatters.consumption(8.5, ConsumptionUnit.LITERS_PER_100_KM, en),
        )
        assertEquals(
            "8,5 L/100 km",
            Formatters.consumption(8.5, ConsumptionUnit.LITERS_PER_100_KM, pt),
        )
        assertEquals(
            "11.8 km/L",
            Formatters.consumption(8.5, ConsumptionUnit.KILOMETERS_PER_LITER, en),
        )
        assertEquals(
            "11,8 km/L",
            Formatters.consumption(8.5, ConsumptionUnit.KILOMETERS_PER_LITER, pt),
        )
    }

    @Test
    fun percentAndPointsAreStableAcrossLocales() {
        assertEquals("80%", Formatters.percent(80.4, en))
        assertEquals("80%", Formatters.percent(80.4, pt))
        assertEquals("7.3", Formatters.points(7.3, en))
        assertEquals("7,3", Formatters.points(7.3, pt))
    }

    @Test
    fun parseDecimalAcceptsCommaAndDot() {
        assertEquals(8.5, Formatters.parseDecimal("8,5") ?: 0.0, 0.0001)
        assertEquals(12.75, Formatters.parseDecimal(" 12.75 ") ?: 0.0, 0.0001)
        assertEquals(1_234.5, Formatters.parseDecimal("1 234,5") ?: 0.0, 0.0001)
        assertNull(Formatters.parseDecimal(""))
        assertNull(Formatters.parseDecimal("abc"))
    }
}
