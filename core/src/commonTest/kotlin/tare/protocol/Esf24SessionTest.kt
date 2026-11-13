package tare.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Esf24SessionTest {
    private val now = 1_800_000_000L

    private fun session(drain: Boolean = true) = Esf24Session(ScaleDisplayUnit.LB, drain) { now }

    private fun List<ScaleEvent>.sent() = filterIsInstance<ScaleEvent.Send>().map { it.bytes.toHex() }

    @Test
    fun fullWeighIn() {
        val s = session()
        assertEquals(listOf("1309150210283700a2"), s.onNotification("120f159ea50b44ac043905320000e8".hexToBytes()).sent())
        // The unit request repeats until answered: answer once.
        assertEquals(emptyList(), s.onNotification("120f159ea50b44ac043905320000e8".hexToBytes()).sent())

        val init = s.onNotification("140b150000010000000035".hexToBytes()).sent()
        assertEquals(listOf(Esf24Protocol.setTimeCommand(now, 0x15).toHex()), init)

        assertEquals(listOf("2204153b"), s.onNotification("210515013c".hexToBytes()).sent())

        val stored = s.onNotification("23141501016e6e73302b48016b013400000000e1".hexToBytes())
        assertTrue(stored.single() is ScaleEvent.StoredReading)

        val settling = s.onNotification("100b152b4800016b013445".hexToBytes())
        assertEquals(listOf(ScaleEvent.Weighing(110.80)), settling)

        val final = s.onNotification("100b152b4801016b013445".hexToBytes())
        assertEquals(listOf("1f05151049"), final.sent())
        assertEquals(ScaleEvent.Reading(110.80, 363, 308), final.filterIsInstance<ScaleEvent.Reading>().single())

        // The scale repeats the final frame until the end command lands: deliver it once.
        assertEquals(emptyList(), s.onNotification("100b152b4801016b013445".hexToBytes()))
    }

    @Test
    fun secondWeighInOnTheSameConnection() {
        val s = session()
        s.onNotification("100b152b4801016b013445".hexToBytes())
        s.onNotification("100b152b4800016b013445".hexToBytes())
        val again = s.onNotification("100b152b4801016b013445".hexToBytes())
        assertEquals(1, again.filterIsInstance<ScaleEvent.Reading>().size)
    }

    @Test
    fun noStoredQueryWhenDrainIsOff() {
        val s = session(drain = false)
        assertEquals(emptyList(), s.onNotification("210515013c".hexToBytes()).sent())
    }

    @Test
    fun learnsAndEchoesVendorByte() {
        val s = session()
        val reply = s.onNotification("1210ff7720760003ff040505000a054d".hexToBytes()).sent()
        assertEquals(0xFF, s.vendorByte)
        assertTrue(reply.single().startsWith("1309ff02"))
        val final = s.onNotification("100bff14f00101fb01f30f".hexToBytes())
        assertEquals(listOf("1f05ff1033"), final.sent())
        assertEquals(ScaleEvent.Reading(53.60, 507, 499), final.filterIsInstance<ScaleEvent.Reading>().single())
    }
}
