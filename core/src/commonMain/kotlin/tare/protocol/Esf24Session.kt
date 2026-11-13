package tare.protocol

/**
 * One connection's conversation with an ESF-24, as a pure state machine: feed it each
 * notification and it returns the commands to write back and the readings to keep.
 *
 * A weigh-in runs:
 *
 * 1. scale → `12` unit request, repeated until answered. Its byte 2 is the vendor byte.
 * 2. app → `13` set unit.
 * 3. scale → `14` measurement-init request; app → `20` set time; scale → `21` ack.
 * 4. app → `22` stored-reading query (only when [drainStoredReadings]); scale → `23` records.
 * 5. scale → `10` frames while the weight settles, then a final `10` frame with impedance.
 *    It repeats the final frame until app → `1f` end measurement.
 *
 * One connection can carry several weigh-ins, so the next settling frame re-arms the final.
 */
class Esf24Session(
    private val displayUnit: ScaleDisplayUnit,
    private val drainStoredReadings: Boolean,
    private val nowUnixSeconds: () -> Long,
) : ScaleSession {
    var vendorByte: Int = Esf24Protocol.DEFAULT_VENDOR_BYTE
        private set

    private var unitSent = false
    private var timeSent = false
    private var storedQueried = false
    private var finalDelivered = false

    override fun onNotification(bytes: ByteArray): List<ScaleEvent> {
        val events = mutableListOf<ScaleEvent>()
        when (val frame = Esf24Protocol.decode(bytes, vendorByte)) {
            is Esf24Frame.UnitRequest -> {
                if (frame.vendorByte != vendorByte) {
                    vendorByte = frame.vendorByte
                    events += ScaleEvent.Log("vendor byte 0x${frame.vendorByte.toString(16)}")
                }
                if (!unitSent) {
                    unitSent = true
                    events += ScaleEvent.Send(Esf24Protocol.setUnitCommand(displayUnit, vendorByte))
                }
            }

            Esf24Frame.MeasurementInitRequest -> if (!timeSent) {
                timeSent = true
                events += ScaleEvent.Send(Esf24Protocol.setTimeCommand(nowUnixSeconds(), vendorByte))
            }

            Esf24Frame.SetTimeAck -> if (drainStoredReadings && !storedQueried) {
                storedQueried = true
                events += ScaleEvent.Send(Esf24Protocol.storedQueryCommand(vendorByte))
            }

            is Esf24Frame.Measurement -> if (frame.isFinal) {
                if (!finalDelivered) {
                    finalDelivered = true
                    events += ScaleEvent.Send(Esf24Protocol.endMeasurementCommand(vendorByte))
                    events += ScaleEvent.Reading(frame.weightKg, frame.impedanceOhms, frame.impedanceHighOhms)
                }
            } else {
                finalDelivered = false
                events += ScaleEvent.Weighing(frame.weightKg)
            }

            is Esf24Frame.StoredRecord -> events += ScaleEvent.StoredReading(
                takenAtUnixSeconds = frame.takenAtUnixSeconds,
                weightKg = frame.weightKg,
                impedanceOhms = frame.impedanceOhms,
                impedanceHighOhms = frame.impedanceHighOhms,
            )

            Esf24Frame.StoreEmpty -> events += ScaleEvent.Log("no stored readings")
            is Esf24Frame.Malformed -> events += ScaleEvent.Log("${frame.reason}: ${frame.bytes.toHex()}")
            is Esf24Frame.Unknown -> events += ScaleEvent.Log("ignored ${frame.bytes.toHex()}")
        }
        return events
    }
}
