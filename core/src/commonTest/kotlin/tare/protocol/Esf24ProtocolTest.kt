package tare.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Frames are real captures from ESF-24 units (and, for vendor byte 0xff, a protocol-identical QN
// scale), as published in etekcity_esf551_ble's test suite (MIT). See THIRD_PARTY_NOTICES.md.
class Esf24ProtocolTest {
    private fun decode(hex: String, vendor: Int = 0x15) = Esf24Protocol.decode(hex.hexToBytes(), vendor)

    @Test
    fun finalMeasurementCarriesWeightAndBothImpedances() {
        val m = assertIs<Esf24Frame.Measurement>(decode("100b152b4801016b013445"))
        assertEquals(110.80, m.weightKg)
        assertTrue(m.isFinal)
        assertEquals(363, m.impedanceOhms)
        assertEquals(308, m.impedanceHighOhms)
    }

    @Test
    fun settlingFrameIsNotFinal() {
        val m = assertIs<Esf24Frame.Measurement>(decode("100b152b4800016b013445"))
        assertEquals(false, m.isFinal)
        assertEquals(110.80, m.weightKg)
    }

    @Test
    fun zeroImpedanceMeansNotMeasured() {
        val m = assertIs<Esf24Frame.Measurement>(decode("100b152b4801016b000045"))
        assertEquals(363, m.impedanceOhms)
        assertNull(m.impedanceHighOhms)
        val noBia = assertIs<Esf24Frame.Measurement>(decode("100b152378010000000000cc"))
        assertEquals(90.80, noBia.weightKg)
        assertNull(noBia.impedanceOhms)
    }

    @Test
    fun twelveByteFirmwareVariantUsesSameOffsets() {
        val m = assertIs<Esf24Frame.Measurement>(decode("100b15230f01022501f80083"))
        assertEquals(89.75, m.weightKg)
        assertEquals(549, m.impedanceOhms)
        assertEquals(504, m.impedanceHighOhms)
    }

    @Test
    fun otherQnMeasurementFlavoursAreIgnored() {
        assertIs<Esf24Frame.Unknown>(decode("100e1504540100016b0134000045"))
        assertIs<Esf24Frame.Unknown>(decode("100b16"))
    }

    @Test
    fun measurementMustEchoTheSessionsVendorByte() {
        val final = "100bff14f00101fb01f30f"
        assertIs<Esf24Frame.Unknown>(decode(final))
        val m = assertIs<Esf24Frame.Measurement>(decode(final, vendor = 0xFF))
        assertEquals(53.60, m.weightKg)
        assertEquals(507, m.impedanceOhms)
    }

    @Test
    fun unitRequestAcceptsAnyLengthAndVendorByte() {
        assertEquals(0x15, assertIs<Esf24Frame.UnitRequest>(decode("120f159ea50b44ac043905320000e8")).vendorByte)
        assertEquals(0xFF, assertIs<Esf24Frame.UnitRequest>(decode("1210ff7720760003ff040505000a054d")).vendorByte)
        assertIs<Esf24Frame.Unknown>(decode("120f15"))
    }

    @Test
    fun handshakeFrames() {
        assertIs<Esf24Frame.MeasurementInitRequest>(decode("140b150000010000000035"))
        assertIs<Esf24Frame.MeasurementInitRequest>(decode("140bff000001000000001f", vendor = 0xFF))
        assertIs<Esf24Frame.SetTimeAck>(decode("210515013c"))
        assertIs<Esf24Frame.SetTimeAck>(decode("2104ff0125", vendor = 0xFF))
        assertIs<Esf24Frame.Unknown>(decode("21051501"))
    }

    @Test
    fun storedRecordsDecode() {
        val r = assertIs<Esf24Frame.StoredRecord>(decode("23141501016e6e73302b48016b013400000000e1"))
        assertEquals(1, r.count)
        assertEquals(1, r.index)
        assertEquals(0x30736E6EL + Esf24Protocol.SCALE_EPOCH_UNIX_SECONDS, r.takenAtUnixSeconds)
        assertEquals(110.80, r.weightKg)
        assertEquals(363, r.impedanceOhms)
        assertEquals(308, r.impedanceHighOhms)

        val sixteen = assertIs<Esf24Frame.StoredRecord>(decode("231015020112213932238c023c0216ee"))
        assertEquals(91.00, sixteen.weightKg)
        assertEquals(572, sixteen.impedanceOhms)
        assertEquals(534, sixteen.impedanceHighOhms)

        val nineteen = assertIs<Esf24Frame.StoredRecord>(decode("2313ff04015dcdb6311cd901fc01f00000002e", 0xFF))
        assertEquals(73.85, nineteen.weightKg)

        val weightOnly = assertIs<Esf24Frame.StoredRecord>(decode("2314150601013d5b302bb60000000000000000fd"))
        assertEquals(111.90, weightOnly.weightKg)
        assertNull(weightOnly.impedanceOhms)
    }

    @Test
    fun storedRecordChecks() {
        assertIs<Esf24Frame.StoreEmpty>(decode("231415000000000000000000000000000000004c"))
        val bad = "231015020112213932238c023c0216ee".hexToBytes().also { it[9] = (it[9].toInt() xor 1).toByte() }
        assertIs<Esf24Frame.Malformed>(Esf24Protocol.decode(bad, 0x15))
        assertIs<Esf24Frame.Malformed>(decode("23141501016e6e7330"))
    }

    @Test
    fun commandsMatchCaptures() {
        assertEquals("1309150110283700a1", Esf24Protocol.setUnitCommand(ScaleDisplayUnit.KG, 0x15).toHex())
        assertEquals("1309150210283700a2", Esf24Protocol.setUnitCommand(ScaleDisplayUnit.LB, 0x15).toHex())
        assertEquals("1309150810283700a8", Esf24Protocol.setUnitCommand(ScaleDisplayUnit.ST, 0x15).toHex())
        assertEquals("1f05151049", Esf24Protocol.endMeasurementCommand(0x15).toHex())
        assertEquals("1f05ff1033", Esf24Protocol.endMeasurementCommand(0xFF).toHex())
        assertEquals("2204153b", Esf24Protocol.storedQueryCommand(0x15).toHex())
        assertEquals("2204ff25", Esf24Protocol.storedQueryCommand(0xFF).toHex())
    }

    @Test
    fun setTimeIsLittleEndianSecondsFromScaleEpoch() {
        val now = 1_700_000_000L
        val cmd = Esf24Protocol.setTimeCommand(now, 0x15)
        assertEquals(8, cmd.size)
        assertEquals("200815", cmd.copyOfRange(0, 3).toHex())
        assertEquals(now - Esf24Protocol.SCALE_EPOCH_UNIX_SECONDS, cmd.u32le(3))
        assertEquals(Esf24Protocol.checksum(cmd, 7), cmd.u8(7))
    }
}
