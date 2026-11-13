package tare.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitsTest {
    @Test
    fun formatsWeights() {
        assertEquals(DisplayWeight("173.1", "lb"), WeightFormat.display(78.52, WeightUnit.LB))
        assertEquals(DisplayWeight("78.5", "kg"), WeightFormat.display(78.52, WeightUnit.KG))
        assertEquals(DisplayWeight("12 st 5.1", "lb"), WeightFormat.display(78.52, WeightUnit.ST))
        assertEquals(DisplayWeight("12:5.1", "st"), WeightFormat.compact(78.52, WeightUnit.ST))
        assertEquals(DisplayWeight("2.3", "lb"), WeightFormat.delta(-1.04, WeightUnit.LB))
    }

    @Test
    fun parsesInput() {
        assertEquals(78.52, WeightFormat.parse("78,52", WeightUnit.KG))
        assertEquals(78.52, WeightFormat.parse("173.1", WeightUnit.LB)!!, 0.01)
        assertEquals(78.52, WeightFormat.parse("12st 5.1", WeightUnit.ST)!!, 0.01)
        assertEquals(78.52, WeightFormat.parse("12 5.1", WeightUnit.ST)!!, 0.01)
        assertNull(WeightFormat.parse("abc", WeightUnit.KG))
        assertNull(WeightFormat.parse("0", WeightUnit.KG))
    }

    @Test
    fun formatDecimalRoundsHalfAwayFromZero() {
        assertEquals("2.5", formatDecimal(2.45, 1))
        assertEquals("-1.0", formatDecimal(-0.95, 1))
        assertEquals("0.0", formatDecimal(-0.01, 1))
        assertEquals("12", formatDecimal(11.6, 0))
        assertEquals("3.05", formatDecimal(3.05, 2))
    }

    @Test
    fun formatsHeights() {
        assertEquals("6′ 0″", HeightFormat.text(182.0, imperial = true))
        assertEquals("182 cm", HeightFormat.text(182.0, imperial = false))
    }
}
