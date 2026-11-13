package tare.protocol

/** One connection's conversation with a scale: feed it each notification, act on what it returns. */
interface ScaleSession {
    fun onNotification(bytes: ByteArray): List<ScaleEvent>
}

sealed interface ScaleEvent {
    class Send(val bytes: ByteArray) : ScaleEvent {
        override fun toString() = "Send(${bytes.toHex()})"
    }

    /** A live weight while the scale settles; nothing to save yet. */
    data class Weighing(val weightKg: Double) : ScaleEvent

    /**
     * The finished weigh-in. Impedances are null when the scale couldn't measure them (e.g. socks);
     * [impedanceHighOhms] (500 kHz) is ESF-24 only.
     */
    data class Reading(val weightKg: Double, val impedanceOhms: Int?, val impedanceHighOhms: Int?) : ScaleEvent

    /** A reading the scale stored while nothing was connected. Delivering it deleted it from the scale. */
    data class StoredReading(
        val takenAtUnixSeconds: Long,
        val weightKg: Double,
        val impedanceOhms: Int?,
        val impedanceHighOhms: Int?,
    ) : ScaleEvent

    data class Log(val message: String) : ScaleEvent
}
