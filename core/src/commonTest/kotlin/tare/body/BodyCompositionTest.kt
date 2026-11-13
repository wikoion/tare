package tare.body

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Expected values match the vendor app on real hardware, as recorded in etekcity_esf551_ble's
// test suite (MIT). See THIRD_PARTY_NOTICES.md.
class BodyCompositionTest {
    @Test
    fun bodyFatMatchesVendorApp() {
        assertEquals(20.7, BodyComposition(74.50, 170.0, 43, Sex.MALE, 524).bodyFatPercent)
        assertEquals(29.5, BodyComposition(80.58, 175.0, 26, Sex.FEMALE, 525).bodyFatPercent)
    }

    @Test
    fun athleteModeMatchesVendorApp() {
        assertEquals(14.5, BodyComposition(74.55, 170.0, 43, Sex.MALE, 526, athlete = true).bodyFatPercent)
        val female = BodyComposition(80.50, 175.0, 26, Sex.FEMALE, 525, athlete = true)
        assertEquals(20.7, female.bodyFatPercent)
        assertEquals(63.84, female.fatFreeMassKg)
        assertEquals(1748, female.basalMetabolicRate)
        assertEquals(5.0, BodyComposition(35.65, 180.0, 20, Sex.MALE, 800, athlete = true).bodyFatPercent)
    }

    @Test
    fun bmiIsTruncatedToTwoDecimals() {
        assertEquals(25.77, BodyComposition.bmi(74.50, 170.0))
    }

    @Test
    fun derivedMetricsAreConsistent() {
        // Worked by hand from the formulas: BMI 25.77, fat 20.7 %, fat-free mass 59.08 kg.
        val c = BodyComposition(74.50, 170.0, 43, Sex.MALE, 524)
        assertEquals(59.08, c.fatFreeMassKg)
        assertEquals(2.95, c.boneMassKg)                     // 0.05 × 59.08
        assertEquals(56.13, c.muscleMassKg)                  // 59.08 − 2.954
        assertEquals(57.3, c.bodyWaterPercent)               // 0.76 × 56.126 / 74.5
        assertEquals(51.2, c.skeletalMusclePercent)          // 0.68 × 56.126 / 74.5
        assertEquals(1646, c.basalMetabolicRate)             // 59.08 × 21.6 + 370
        assertEquals(8, c.visceralFat)                       // 0.8666·25.77 + 0.0082·20.7 + 0.026·15.42 − 14.2692
        assertEquals(18.2, c.subcutaneousFatPercent)         // 0.965·20.7 − 0.22·8
        assertEquals(18.0, c.proteinPercent)                 // 100 − 20.7 − 3.96 − 57.3
        assertTrue(c.metabolicAge in 18..51)
    }
}
