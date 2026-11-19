package app.ecorota.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money is displayed to the cent, so sums have to add up the same cents the rows
 * show. Summing raw doubles and rounding only at the end makes a day total that
 * differs from its own trips by a cent.
 */
object Money {

    fun roundToCents(value: Double): Double =
        BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()

    fun sumToCents(values: Iterable<Double>): Double =
        values.sumOf { roundToCents(it) }
}
