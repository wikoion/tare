package tare.protocol

/**
 * Wire format of the Etekcity ESF-551, which runs on Etekcity's own Bluetooth platform rather than
 * the ESF-24's QN one. There is no handshake: once the app subscribes to FFF1, the scale notifies a
 * 22-byte frame for every weight update while someone stands on it.
 *
 * ```
 * 0..1    a5 02
 * 2       sequence number
 * 3..4    10 00
 * 5       checksum (not checked)
 * 6..9    01 61 a1 00
 * 10..12  weight, little-endian, grams
 * 13..14  impedance, little-endian, ohms
 * 15..18  not decoded
 * 19      1 once the weight has settled (the final frame), else 0
 * 20      1 when byte 13..14 holds a measured impedance
 * 21      display unit: 0 kg, 1 lb, 2 st
 * ```
 *
 * The app can change the display unit by writing a command to FFF2. Frame layout and command
 * come from the community reverse-engineering in `etekcity_esf551_ble` (MIT, Ron Ben Yosef). This
 * is an independent Kotlin port of that library; see THIRD_PARTY_NOTICES.md.
 */
object Esf551Protocol {
    const val FRAME_SIZE = 22

    private val header = byteArrayOf(0xa5.toByte(), 0x02)
    private val marker = byteArrayOf(0x01, 0x61, 0xa1.toByte(), 0x00)

    /** True for any ESF-551 weight frame, settling or final. */
    fun isFrame(frame: ByteArray): Boolean =
        frame.size == FRAME_SIZE &&
            frame.u8(0) == header.u8(0) && frame.u8(1) == header.u8(1) &&
            frame.u8(3) == 0x10 && frame.u8(4) == 0x00 &&
            (0 until 4).all { frame.u8(6 + it) == marker.u8(it) }

    fun decode(frame: ByteArray): Esf551Frame? {
        if (!isFrame(frame)) return null
        val grams = frame.u8(10) or (frame.u8(11) shl 8) or (frame.u8(12) shl 16)
        val impedance = frame.u8(13) or (frame.u8(14) shl 8)
        return Esf551Frame(
            weightKg = grams / 1000.0,
            isFinal = frame.u8(19) == 1,
            impedanceOhms = impedance.takeIf { frame.u8(20) == 1 && it != 0 },
            displayUnitCode = frame.u8(21),
        )
    }

    /** The display-unit code the scale uses in byte 21 of its frames and in [setUnitCommand]. */
    fun unitCode(unit: ScaleDisplayUnit): Int = when (unit) {
        ScaleDisplayUnit.KG -> 0
        ScaleDisplayUnit.LB -> 1
        ScaleDisplayUnit.ST -> 2
    }

    /** Sets the unit on the scale's display. Byte 5 is 43 minus the unit code. */
    fun setUnitCommand(unit: ScaleDisplayUnit): ByteArray {
        val code = unitCode(unit)
        return intArrayOf(0xa5, 0x22, 0x03, 0x05, 0x00, 43 - code, 0x01, 0x63, 0xa1, 0x00, code)
            .map { it.toByte() }.toByteArray()
    }
}

/** One ESF-551 weight frame. [impedanceOhms] is null when the scale didn't measure it (e.g. socks). */
data class Esf551Frame(
    val weightKg: Double,
    val isFinal: Boolean,
    val impedanceOhms: Int?,
    val displayUnitCode: Int,
)
