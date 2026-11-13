package tare.protocol

/**
 * Wire format of the Etekcity ESF-24 and the protocol-identical ESF-17/ESF-18.
 *
 * These are QN-platform ("QN-Scale") devices. They talk over GATT service FFF0: the scale
 * notifies on FFF1 and takes commands on FFF2. Every frame, in both directions, is
 *
 * ```
 * [opcode] [length] [vendor byte] [payload ...] [checksum]
 * ```
 *
 * The checksum is the low byte of the sum of all preceding bytes. The vendor byte varies per
 * unit (0x15 on most ESF-24s, 0xff on some). The scale states it in its unit request (opcode
 * 0x12) and every reply must echo it.
 *
 * What each frame means comes from the community reverse-engineering in `etekcity_esf551_ble`
 * (MIT, Ron Ben Yosef). This is an independent Kotlin port of that library; see
 * THIRD_PARTY_NOTICES.md.
 */
object Esf24Protocol {
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    const val WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

    const val DEFAULT_VENDOR_BYTE = 0x15

    /** The scale's clock counts seconds from 2000-01-01 00:00 in UTC+8 (China Standard Time). */
    const val SCALE_EPOCH_UNIX_SECONDS = 946_656_000L

    private const val OP_MEASUREMENT = 0x10
    private const val OP_UNIT_REQUEST = 0x12
    private const val OP_SET_UNIT = 0x13
    private const val OP_MEASUREMENT_INIT = 0x14
    private const val OP_END_MEASUREMENT = 0x1F
    private const val OP_SET_TIME = 0x20
    private const val OP_SET_TIME_ACK = 0x21
    private const val OP_STORED_QUERY = 0x22
    private const val OP_STORED_RECORD = 0x23

    // The measurement and init frames are recognised by opcode *and* length byte: other QN
    // scales send 0x10 frames with a different length byte and different field offsets.
    private const val MEASUREMENT_LENGTH_BYTE = 0x0B
    private const val STATUS_FINAL = 0x01

    /**
     * Decodes one notification. [vendorByte] is the session's vendor byte. Only the unit
     * request is accepted with any vendor byte, because that is where the session learns it.
     */
    fun decode(frame: ByteArray, vendorByte: Int): Esf24Frame {
        if (frame.size < 3) return Esf24Frame.Unknown(frame)
        val op = frame.u8(0)
        val vendorMatches = frame.u8(2) == vendorByte
        return when {
            // 15 bytes on most units, 16 or 17 on some firmware: only a minimum is checked.
            op == OP_UNIT_REQUEST && frame.size >= 10 -> Esf24Frame.UnitRequest(frame.u8(2))

            // 11 bytes, or 12 on a firmware variant with a pad byte before the checksum.
            op == OP_MEASUREMENT && frame.size >= 11 &&
                frame.u8(1) == MEASUREMENT_LENGTH_BYTE && vendorMatches -> {
                Esf24Frame.Measurement(
                    weightKg = frame.u16be(3) / 100.0,
                    isFinal = frame.u8(5) == STATUS_FINAL,
                    impedanceOhms = frame.u16be(6).takeIf { it != 0 },
                    impedanceHighOhms = frame.u16be(8).takeIf { it != 0 },
                )
            }

            op == OP_MEASUREMENT_INIT && frame.size >= 11 &&
                frame.u8(1) == MEASUREMENT_LENGTH_BYTE && vendorMatches ->
                Esf24Frame.MeasurementInitRequest

            // Seen as `21 05 15 01 3c` and `21 04 ff 01 25`: five bytes either way.
            op == OP_SET_TIME_ACK && frame.size == 5 && frame.u8(1) in 4..5 && vendorMatches ->
                Esf24Frame.SetTimeAck

            op == OP_STORED_RECORD -> decodeStoredRecord(frame, vendorMatches)

            else -> Esf24Frame.Unknown(frame)
        }
    }

    /**
     * A stored (offline) reading. 20 bytes on the ESF-24, 16 or 19 on other firmware; the
     * fields sit at the same offsets with the checksum last:
     *
     * ```
     * 0..2    23 <len> <vendor>
     * 3       records in this batch (0 = store empty)
     * 4       1-based index of this record
     * 5..8    timestamp, little-endian uint32, scale epoch
     * 9..10   weight, big-endian, 0.01 kg
     * 11..12  impedance at 50 kHz, ohms (0 = not measured)
     * 13..14  impedance at 500 kHz, ohms (0 = not measured)
     * ```
     */
    private fun decodeStoredRecord(frame: ByteArray, vendorMatches: Boolean): Esf24Frame {
        if (frame.size < 16 || frame.u8(1) != frame.size || !vendorMatches) {
            return Esf24Frame.Malformed(frame, "stored record with unexpected length or vendor byte")
        }
        if (frame.u8(frame.size - 1) != checksum(frame, frame.size - 1)) {
            return Esf24Frame.Malformed(frame, "stored record failed its checksum")
        }
        val count = frame.u8(3)
        if (count == 0) return Esf24Frame.StoreEmpty
        return Esf24Frame.StoredRecord(
            count = count,
            index = frame.u8(4),
            takenAtUnixSeconds = frame.u32le(5) + SCALE_EPOCH_UNIX_SECONDS,
            weightKg = frame.u16be(9) / 100.0,
            impedanceOhms = frame.u16be(11).takeIf { it != 0 },
            impedanceHighOhms = frame.u16be(13).takeIf { it != 0 },
        )
    }

    /** Answers the unit request and sets the scale's display unit. */
    fun setUnitCommand(unit: ScaleDisplayUnit, vendorByte: Int): ByteArray =
        command(OP_SET_UNIT, 0x09, vendorByte, unit.code, 0x10, 0x28, 0x37, 0x00)

    /** Answers the measurement-init request with the current time. */
    fun setTimeCommand(nowUnixSeconds: Long, vendorByte: Int): ByteArray {
        val t = nowUnixSeconds - SCALE_EPOCH_UNIX_SECONDS
        return command(
            OP_SET_TIME, 0x08, vendorByte,
            (t and 0xFF).toInt(), ((t shr 8) and 0xFF).toInt(),
            ((t shr 16) and 0xFF).toInt(), ((t shr 24) and 0xFF).toInt(),
        )
    }

    /** Stops the scale repeating its final frame. */
    fun endMeasurementCommand(vendorByte: Int): ByteArray =
        command(OP_END_MEASUREMENT, 0x05, vendorByte, 0x10)

    /**
     * Asks for readings taken while nothing was connected. The scale sends one record per
     * reading and deletes each one as it is delivered, so the vendor app won't see them.
     */
    fun storedQueryCommand(vendorByte: Int): ByteArray =
        command(OP_STORED_QUERY, 0x04, vendorByte)

    private fun command(vararg bytes: Int): ByteArray {
        val out = ByteArray(bytes.size + 1)
        bytes.forEachIndexed { i, b -> out[i] = b.toByte() }
        out[bytes.size] = checksum(out, bytes.size).toByte()
        return out
    }

    internal fun checksum(bytes: ByteArray, length: Int): Int {
        var sum = 0
        for (i in 0 until length) sum += bytes[i].toInt() and 0xFF
        return sum and 0xFF
    }
}

/** The unit shown on the scale's own display. */
enum class ScaleDisplayUnit(val code: Int) { KG(0x01), LB(0x02), ST(0x08) }

sealed interface Esf24Frame {
    /** Sent every ~200 ms after connecting until a set-unit command answers it. */
    data class UnitRequest(val vendorByte: Int) : Esf24Frame

    /** Streams while the weight settles; only the final frame carries impedance. */
    data class Measurement(
        val weightKg: Double,
        val isFinal: Boolean,
        val impedanceOhms: Int?,
        val impedanceHighOhms: Int?,
    ) : Esf24Frame

    data object MeasurementInitRequest : Esf24Frame
    data object SetTimeAck : Esf24Frame
    data object StoreEmpty : Esf24Frame

    data class StoredRecord(
        val count: Int,
        val index: Int,
        val takenAtUnixSeconds: Long,
        val weightKg: Double,
        val impedanceOhms: Int?,
        val impedanceHighOhms: Int?,
    ) : Esf24Frame

    class Malformed(val bytes: ByteArray, val reason: String) : Esf24Frame
    class Unknown(val bytes: ByteArray) : Esf24Frame
}

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
internal fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
internal fun ByteArray.u32le(i: Int): Long =
    u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd-length hex string" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
