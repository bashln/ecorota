package app.ecorota.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelUnitsTest {

    @Test
    fun `liters per hundred kilometers pass through unchanged`() {
        assertEquals(8.5, FuelUnits.toLitersPer100Km(8.5, ConsumptionUnit.LITERS_PER_100_KM), 0.0)
        assertEquals(12.0, FuelUnits.fromLitersPer100Km(12.0, ConsumptionUnit.LITERS_PER_100_KM), 0.0)
    }

    @Test
    fun `kilometers per liter converts to liters per hundred kilometers`() {
        assertEquals(10.0, FuelUnits.toLitersPer100Km(10.0, ConsumptionUnit.KILOMETERS_PER_LITER), 1e-12)
        assertEquals(8.0, FuelUnits.toLitersPer100Km(12.5, ConsumptionUnit.KILOMETERS_PER_LITER), 1e-12)
        assertEquals(
            11.764705882352942,
            FuelUnits.fromLitersPer100Km(8.5, ConsumptionUnit.KILOMETERS_PER_LITER),
            1e-12,
        )
    }

    @Test
    fun `conversions round trip within floating point tolerance`() {
        for (value in listOf(4.0, 8.5, 12.5, 20.0)) {
            val asKmL = FuelUnits.fromLitersPer100Km(value, ConsumptionUnit.KILOMETERS_PER_LITER)
            assertEquals(
                value,
                FuelUnits.toLitersPer100Km(asKmL, ConsumptionUnit.KILOMETERS_PER_LITER),
                1e-9,
            )
        }
    }

    @Test
    fun `non positive values fall back to a usable default`() {
        assertEquals(
            VehicleProfile.DEFAULT_CONSUMPTION_L_PER_100_KM,
            FuelUnits.toLitersPer100Km(0.0, ConsumptionUnit.KILOMETERS_PER_LITER),
            0.0,
        )
        assertEquals(
            VehicleProfile.DEFAULT_CONSUMPTION_L_PER_100_KM,
            FuelUnits.toLitersPer100Km(-3.0, ConsumptionUnit.KILOMETERS_PER_LITER),
            0.0,
        )
        assertEquals(0.0, FuelUnits.fromLitersPer100Km(0.0, ConsumptionUnit.KILOMETERS_PER_LITER), 0.0)
        assertEquals(0.0, FuelUnits.fromLitersPer100Km(-1.0, ConsumptionUnit.KILOMETERS_PER_LITER), 0.0)
    }
}
