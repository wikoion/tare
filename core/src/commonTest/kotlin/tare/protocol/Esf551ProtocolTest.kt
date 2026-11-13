package tare.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The settling/final pair are real ESF-551 captures and the others synthetic frames, as published
// in etekcity_esf551_ble's test suite (MIT). See THIRD_PARTY_NOTICES.md.
class Esf551ProtocolTest {
    private val live = "a502cc10004d0161a1000c1b0100003d2d3267000100"
    private val final = "a502cd1000490161a1000c1b0100003f2d3267010100"

    @Test
    fun finalFrameCarriesWeightInGrams() {
        val f = assertNotNull(Esf551Protocol.decode(final.hexToBytes()))
        assertTrue(f.isFinal)
        assertEquals(72.46, f.weightKg)
        assertNull(f.impedanceOhms) // flagged as measured, but zero
        assertEquals(0, f.displayUnitCode)
    }

    @Test
    fun settlingFrameIsNotFinal() {
        val f = assertNotNull(Esf551Protocol.decode(live.hexToBytes()))
        assertFalse(f.isFinal)
        assertEquals(72.46, f.weightKg)
    }

    @Test
    fun impedanceCountsOnlyWhenFlagged() {
        val withImpedance = assertNotNull(Esf551Protocol.decode("a502001000000161a100e80300640000000000010100".hexToBytes()))
        assertEquals(1.0, withImpedance.weightKg)
        assertEquals(100, withImpedance.impedanceOhms)
        val without = assertNotNull(Esf551Protocol.decode("a502001000000161a100e80300640000000000010000".hexToBytes()))
        assertNull(without.impedanceOhms)
    }

    @Test
    fun rejectsOtherFrames() {
        assertNull(Esf551Protocol.decode(ByteArray(22)))
        assertNull(Esf551Protocol.decode(final.hexToBytes().copyOf(21)))
        assertNull(Esf551Protocol.decode("100b152b4801016b013445".hexToBytes())) // an ESF-24 frame
    }

    @Test
    fun unitCommand() {
        assertEquals("a5220305002b0163a10000", Esf551Protocol.setUnitCommand(ScaleDisplayUnit.KG).toHex())
        assertEquals("a5220305002a0163a10001", Esf551Protocol.setUnitCommand(ScaleDisplayUnit.LB).toHex())
        assertEquals("a522030500290163a10002", Esf551Protocol.setUnitCommand(ScaleDisplayUnit.ST).toHex())
    }
}
