package tare.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Esf551SessionTest {
    // Synthetic frames in the captured layout: weight in grams, impedance, flags, unit (0 = kg).
    private fun frame(grams: Int, final: Boolean, impedance: Int = 0, unit: Int = 0): ByteArray {
        val b = "a502001000000161a10000000000000000000000000000".hexToBytes().copyOf(22)
        b[10] = grams.toByte(); b[11] = (grams shr 8).toByte(); b[12] = (grams shr 16).toByte()
        b[13] = impedance.toByte(); b[14] = (impedance shr 8).toByte()
        b[19] = if (final) 1 else 0
        b[20] = if (impedance != 0) 1 else 0
        b[21] = unit.toByte()
        return b
    }

    private fun List<ScaleEvent>.readings() = filterIsInstance<ScaleEvent.Reading>()

    @Test
    fun weighInReportsLiveWeightThenOneReading() {
        val s = Esf551Session(ScaleDisplayUnit.KG, canSetUnit = true)
        assertEquals(listOf<ScaleEvent>(ScaleEvent.Weighing(40.0)), s.onNotification(frame(40_000, final = false)))
        assertEquals(
            listOf<ScaleEvent>(ScaleEvent.Reading(78.52, 512, null)),
            s.onNotification(frame(78_520, final = true, impedance = 512)),
        )
        // The repeated final frame isn't a second reading.
        assertTrue(s.onNotification(frame(78_520, final = true, impedance = 512)).isEmpty())
    }

    @Test
    fun steppingOffIsNotShownAsALiveWeight() {
        val s = Esf551Session(ScaleDisplayUnit.KG, canSetUnit = true)
        s.onNotification(frame(78_520, final = true))
        assertTrue(s.onNotification(frame(30_000, final = false)).isEmpty())
        assertTrue(s.onNotification(frame(0, final = false)).isEmpty())
        // A second weigh-in in the same connection, even at the same weight, counts again.
        assertEquals(listOf<ScaleEvent>(ScaleEvent.Weighing(50.0)), s.onNotification(frame(50_000, final = false)))
        assertEquals(1, s.onNotification(frame(78_520, final = true)).readings().size)
    }

    @Test
    fun setsTheDisplayUnitOnceAfterAReading() {
        val s = Esf551Session(ScaleDisplayUnit.LB, canSetUnit = true)
        val first = s.onNotification(frame(78_520, final = true, unit = 0))
        assertEquals(1, first.readings().size)
        assertEquals(listOf("a5220305002a0163a10001"), first.filterIsInstance<ScaleEvent.Send>().map { it.bytes.toHex() })
        s.onNotification(frame(0, final = false))
        val second = s.onNotification(frame(78_570, final = true, unit = 0))
        assertTrue(second.filterIsInstance<ScaleEvent.Send>().isEmpty())
    }

    @Test
    fun leavesTheUnitAloneWhenItMatchesOrCantBeSet() {
        val matching = Esf551Session(ScaleDisplayUnit.LB, canSetUnit = true)
        assertTrue(matching.onNotification(frame(78_520, final = true, unit = 1)).filterIsInstance<ScaleEvent.Send>().isEmpty())
        val japan = Esf551Session(ScaleDisplayUnit.LB, canSetUnit = false)
        assertTrue(japan.onNotification(frame(78_520, final = true, unit = 0)).filterIsInstance<ScaleEvent.Send>().isEmpty())
    }
}
