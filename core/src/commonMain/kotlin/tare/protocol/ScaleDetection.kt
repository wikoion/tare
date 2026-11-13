package tare.protocol

/**
 * Recognises a supported scale from its advertisement. There are two families:
 *
 * - QN-platform scales (ESF-24, ESF-17, ESF-18) put manufacturer data under company ID 0xFFFF,
 *   a catch-all many vendors use: model code (big-endian) at bytes 0..1, MAC at bytes 5..10.
 * - Etekcity-platform scales (ESF-551) use Etekcity's company ID 0x06D0: a header byte whose low
 *   nibble is the format generation (0 to 2), MAC at bytes 1..6, model code (big-endian) at 7..8.
 *   VeSync's air purifiers and other products advertise the same shape with their own codes.
 *
 * Other platforms check the embedded MAC against the device address. iOS hides MAC addresses, so
 * here the model code is trusted on its own; for QN scales the advertised name is the fallback.
 */
object ScaleDetection {
    const val QN_COMPANY_ID = 0xFFFF
    const val ETEKCITY_COMPANY_ID = 0x06D0

    private val qnModelCodes = mapOf(
        294 to ScaleModel.ESF24,
        946 to ScaleModel.ESF24,
        211 to ScaleModel.ESF17,
        671 to ScaleModel.ESF18,
    )

    private val etekcityModelCodes = mapOf(
        1 to ScaleModel.ESF551,
        2 to ScaleModel.ESF551,
        12 to ScaleModel.ESF551_JP,
    )

    /** Manufacturer data arguments are the payloads without their two company-ID bytes. */
    fun detect(localName: String?, qnManufacturerData: ByteArray?, etekcityManufacturerData: ByteArray? = null): ScaleModel? {
        if (etekcityManufacturerData != null && isEtekcityFrame(etekcityManufacturerData)) {
            etekcityModelCodes[etekcityManufacturerData.u16be(7)]?.let { return it }
        }
        if (qnManufacturerData != null && qnManufacturerData.size >= 11) {
            qnModelCodes[qnManufacturerData.u16be(0)]?.let { return it }
        }
        if (localName != null && localName.startsWith("QN-Scale", ignoreCase = true)) return ScaleModel.ESF24
        return null
    }

    /**
     * For a device [detect] doesn't know: true if it could still be a scale, such as a new model
     * code or firmware. Pairing lists these separately. A QN frame, an Etekcity frame from something
     * named like a scale, or the FFF0 service in the advertisement qualifies.
     */
    fun mightBeScale(
        localName: String?,
        qnManufacturerData: ByteArray?,
        etekcityManufacturerData: ByteArray?,
        advertisesScaleService: Boolean,
    ): Boolean =
        advertisesScaleService ||
            (qnManufacturerData != null && qnManufacturerData.size >= 11) ||
            (etekcityManufacturerData != null && isEtekcityFrame(etekcityManufacturerData) &&
                localName?.contains("scale", ignoreCase = true) == true)

    private fun isEtekcityFrame(payload: ByteArray): Boolean =
        payload.size >= 9 && (payload.u8(0) and 0x0F) in 0..2
}

/** How a scale talks: QN scales run a handshake ([Esf24Session]); Etekcity ones just stream ([Esf551Session]). */
enum class ScalePlatform { QN, ETEKCITY }

/** Stored by [name] with a pairing, so don't rename entries. */
enum class ScaleModel(val displayName: String, val platform: ScalePlatform, val canSetUnit: Boolean = true) {
    ESF24("ESF-24", ScalePlatform.QN),
    ESF17("ESF-17", ScalePlatform.QN),
    ESF18("ESF-18", ScalePlatform.QN),
    ESF551("ESF-551", ScalePlatform.ETEKCITY),

    /** The Japanese ESF-551 is kg-only, and the unit command drops its connection. */
    ESF551_JP("ESF-551 (Japan)", ScalePlatform.ETEKCITY, canSetUnit = false),
}
