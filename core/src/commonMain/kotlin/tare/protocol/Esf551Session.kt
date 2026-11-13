package tare.protocol

/**
 * One connection's conversation with an ESF-551. The scale streams weight frames on its own, so
 * the session only reports them, and after a weigh-in sets the scale's display to [displayUnit]
 * if it shows something else (not on the Japanese model: it's kg-only, and the command drops
 * its connection).
 *
 * Some units send the final frame twice, tens of milliseconds apart; a final frame matching the
 * last reading is that repeat, until a settling frame starts a new weigh-in.
 */
class Esf551Session(
    private val displayUnit: ScaleDisplayUnit,
    private val canSetUnit: Boolean,
) : ScaleSession {
    private var lastDelivered: Esf551Frame? = null
    private var unitSent = false

    // After a reading the scale keeps sending settling frames as you step off; showing those
    // as a live weight would look like a new weigh-in. Live weights show again after a zero.
    private var showLive = true

    override fun onNotification(bytes: ByteArray): List<ScaleEvent> {
        val frame = Esf551Protocol.decode(bytes) ?: return listOf(ScaleEvent.Log("ignored ${bytes.toHex()}"))
        if (!frame.isFinal) {
            lastDelivered = null
            if (frame.weightKg == 0.0) showLive = true
            return if (showLive && frame.weightKg > 0.0) listOf(ScaleEvent.Weighing(frame.weightKg)) else emptyList()
        }
        if (frame == lastDelivered) return emptyList()
        lastDelivered = frame
        showLive = false
        val events = mutableListOf<ScaleEvent>(ScaleEvent.Reading(frame.weightKg, frame.impedanceOhms, null))
        if (canSetUnit && !unitSent && frame.displayUnitCode != Esf551Protocol.unitCode(displayUnit)) {
            unitSent = true
            events += ScaleEvent.Log("setting the scale's display to ${displayUnit.name.lowercase()}")
            events += ScaleEvent.Send(Esf551Protocol.setUnitCommand(displayUnit))
        }
        return events
    }
}
