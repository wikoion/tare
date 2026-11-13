package tare.ble

import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.LogEngine
import com.juul.kable.logs.Logging
import com.juul.kable.indicate
import com.juul.kable.notify
import com.juul.kable.read
import com.juul.kable.write
import com.juul.kable.writeWithoutResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import tare.data.PairedScale
import tare.data.TareRepository
import tare.data.WeighIn
import tare.platform.RadioState
import tare.platform.appendBluetoothLog
import tare.platform.bluetoothState
import tare.platform.knownPeripheral
import tare.protocol.ScaleEvent
import tare.protocol.Esf24Protocol
import tare.protocol.Esf24Session
import tare.protocol.Esf551Protocol
import tare.protocol.Esf551Session
import tare.protocol.ScaleDetection
import tare.protocol.ScaleModel
import tare.protocol.ScalePlatform
import tare.protocol.ScaleSession
import tare.protocol.toHex
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface ScaleStatus {
    data object Starting : ScaleStatus
    data object Unsupported : ScaleStatus
    data object BluetoothOff : ScaleStatus
    data object Unauthorized : ScaleStatus
    data object NotPaired : ScaleStatus
    data object Pairing : ScaleStatus

    /** Paired and holding a connection request open: the scale connects when stepped on. */
    data object Waiting : ScaleStatus
    data object Connected : ScaleStatus
    data class Weighing(val weightKg: Double) : ScaleStatus
}

/** A nearby device. [model] is null for a device that only might be a scale ([ScaleDetection.mightBeScale]). */
data class FoundScale(val identifier: String, val name: String, val model: ScaleModel?, val rssi: Int)

/**
 * Owns the Bluetooth side. Once a scale is paired it keeps a pending connection to it, which
 * iOS completes whenever the scale wakes (someone steps on it), even with the app in the
 * background. Each connection runs a [ScaleSession] for the scale's platform and saves what it
 * produces.
 */
class ScaleManager(
    private val repo: TareRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    /** Demo mode (screenshots): report the paired scale as waiting and never touch Bluetooth. */
    private val demo: Boolean = false,
) {
    private val _status = MutableStateFlow<ScaleStatus>(ScaleStatus.Starting)
    val status: StateFlow<ScaleStatus> = _status.asStateFlow()

    private val _found = MutableStateFlow<List<FoundScale>>(emptyList())
    val found: StateFlow<List<FoundScale>> = _found.asStateFlow()

    private val _saved = MutableSharedFlow<WeighIn>(extraBufferCapacity = 16)

    /** Every weigh-in this manager saves, live or imported from the scale's store. */
    val saved: SharedFlow<WeighIn> = _saved.asSharedFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var job: Job? = null
    private var started = false
    private var pairing = false

    // Kable logs from its own threads; its lines join the log on [scope] so writes don't interleave.
    private val kableLines = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch { for (line in kableLines) log(line) }
    }

    /**
     * Matches values to characteristics by UUID rather than object identity, which failed on iOS:
     * a read's reply and every fff1 notification arrived but never reached the caller. Also sends
     * Kable's Bluetooth events (connection, discovery, subscriptions, data) to the log.
     */
    private val peripheralSetup: PeripheralBuilder.() -> Unit = {
        forceCharacteristicEqualityByUuid = true
        logging {
            level = Logging.Level.Data
            format = Logging.Format.Compact
            engine = object : LogEngine {
                private fun send(level: String, throwable: Throwable?, tag: String, message: String) {
                    kableLines.trySend("kable $level $tag: $message" + (throwable?.let { " ($it)" } ?: ""))
                }
                override fun verbose(throwable: Throwable?, tag: String, message: String) = send("verbose", throwable, tag, message)
                override fun debug(throwable: Throwable?, tag: String, message: String) = send("debug", throwable, tag, message)
                override fun info(throwable: Throwable?, tag: String, message: String) = send("info", throwable, tag, message)
                override fun warn(throwable: Throwable?, tag: String, message: String) = send("warn", throwable, tag, message)
                override fun error(throwable: Throwable?, tag: String, message: String) = send("error", throwable, tag, message)
                override fun assert(throwable: Throwable?, tag: String, message: String) = send("assert", throwable, tag, message)
            }
        }
    }

    /** Starts the connection loop. Nothing touches Bluetooth (or asks for permission) before this. */
    fun start() {
        if (started) return
        started = true
        restart()
    }

    fun startPairing() {
        started = true
        pairing = true
        job?.cancel()
        _found.value = emptyList()
        job = scope.launch { scanForScales() }
    }

    fun stopPairing() {
        if (pairing) restart()
    }

    fun pair(scale: FoundScale) {
        log("paired ${scale.name} (${scale.model?.displayName ?: "unrecognised"}) ${scale.identifier}")
        repo.updateSettings { it.copy(pairedScale = PairedScale(scale.identifier, scale.model?.displayName ?: scale.name, scale.model)) }
        restart()
    }

    fun forget() {
        repo.updateSettings { it.copy(pairedScale = null) }
        restart()
    }

    private fun restart() {
        pairing = false
        job?.cancel()
        if (demo) {
            _status.value = if (repo.settings.value.pairedScale == null) ScaleStatus.NotPaired else ScaleStatus.Waiting
            return
        }
        job = scope.launch {
            val paired = repo.settings.value.pairedScale
            if (paired == null) {
                awaitBluetooth()
                _status.value = ScaleStatus.NotPaired
            } else {
                connectLoop(paired)
            }
        }
    }

    private suspend fun awaitBluetooth() {
        bluetoothState().first { state ->
            _status.value = when (state) {
                RadioState.On -> return@first true
                RadioState.Off -> ScaleStatus.BluetoothOff
                RadioState.Unauthorized -> ScaleStatus.Unauthorized
                RadioState.Unsupported -> ScaleStatus.Unsupported
                RadioState.Unknown -> ScaleStatus.Starting
            }
            false
        }
    }

    private suspend fun scanForScales() {
        awaitBluetooth()
        _status.value = ScaleStatus.Pairing
        log("scanning")
        val logged = mutableMapOf<String, MutableSet<String>>()
        Scanner().advertisements.collect { adv ->
            val name = adv.name ?: adv.peripheralName
            val qnData = adv.manufacturerData(ScaleDetection.QN_COMPANY_ID)
            val etekcityData = adv.manufacturerData(ScaleDetection.ETEKCITY_COMPANY_ID)
            val model = ScaleDetection.detect(name, qnData, etekcityData)
            val maybe = model == null && ScaleDetection.mightBeScale(name, qnData, etekcityData, SERVICE_UUID in adv.uuids)
            logAdvertisement(adv, name, model?.displayName ?: if (maybe) "maybe" else "skipped", logged)
            if (model == null && !maybe) return@collect
            val scale = FoundScale(adv.identifier.toString(), name ?: model?.displayName ?: "Unnamed device", model, adv.rssi)
            _found.value = (_found.value.filterNot { it.identifier == scale.identifier } + scale)
                .sortedWith(compareBy<FoundScale> { it.model == null }.thenByDescending { it.rssi })
        }
    }

    /**
     * Logs what a nearby device advertises, so a scale Tare doesn't recognise can be identified
     * from the log. Each device is logged when first heard and when its advertisement changes,
     * a few times at most; phones and watches rotate their data constantly.
     */
    private fun logAdvertisement(adv: Advertisement, name: String?, verdict: String, logged: MutableMap<String, MutableSet<String>>) {
        val id = adv.identifier.toString()
        val mfr = adv.manufacturerData?.let { "${it.code.toString(16).padStart(4, '0')}:${it.data.toHex()}" }
        val uuids = adv.uuids.joinToString(",") { it.short() }
        val serviceData = adv.uuids.mapNotNull { uuid -> adv.serviceData(uuid)?.let { "${uuid.short()}=${it.toHex()}" } }
        // A device whose name or service list changes is logged again; for one whose manufacturer
        // data changes, only if it might be a scale.
        val key = listOf(name, adv.manufacturerData?.code, uuids, if (verdict != "skipped") mfr else null).joinToString("|")
        val keys = logged.getOrPut(id) { mutableSetOf() }
        if (key in keys || keys.size >= 5) return
        keys += key
        log(
            "seen \"${name ?: "-"}\" $verdict rssi ${adv.rssi} mfr ${mfr ?: "-"} uuids ${uuids.ifEmpty { "-" }}" +
                (if (serviceData.isEmpty()) "" else " data ${serviceData.joinToString(",")}") +
                " connectable ${adv.isConnectable ?: "-"} id $id",
        )
    }

    private suspend fun connectLoop(paired: PairedScale) {
        log("waiting for ${paired.name} ${paired.identifier}")
        while (currentCoroutineContext().isActive) {
            awaitBluetooth()
            val peripheral = knownPeripheral(paired.identifier, peripheralSetup) ?: rediscover(paired)
            try {
                runConnection(peripheral, paired.model)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("connection ended: ${e.message ?: e::class.simpleName}")
            } finally {
                withContext(NonCancellable) {
                    runCatching { peripheral.disconnect() }
                    peripheral.close()
                }
            }
            delay(1.seconds)
        }
    }

    /** The system can forget a peripheral (Bluetooth reset); find it again by scanning. */
    private suspend fun rediscover(paired: PairedScale): Peripheral {
        _status.value = ScaleStatus.Waiting
        val adv = Scanner().advertisements.first { it.identifier.toString() == paired.identifier }
        return Peripheral(adv, peripheralSetup)
    }

    private suspend fun runConnection(peripheral: Peripheral, model: ScaleModel?) {
        _status.value = ScaleStatus.Waiting
        val connection = peripheral.connect() // suspends until the scale wakes up
        _status.value = ScaleStatus.Connected
        log("connected")
        logServices(peripheral)

        // The ESF-551 may not group its characteristics under FFF0, so look them up by UUID.
        val notify = peripheral.characteristicFor(NOTIFY_UUID) ?: NOTIFY
        val write = peripheral.characteristicFor(WRITE_UUID) ?: WRITE
        logVersions(peripheral)
        val settings = repo.settings.value
        var session: ScaleSession? = null
        val outbox = Channel<ByteArray>(Channel.UNLIMITED)
        connection.launch {
            for (bytes in outbox) {
                try {
                    val platform = if (session is Esf551Session) ScalePlatform.ETEKCITY else ScalePlatform.QN
                    peripheral.write(write, bytes, writeTypeFor(peripheral, platform))
                    log("→ ${bytes.toHex()}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("→ ${bytes.toHex()} failed: ${e.message ?: e::class.simpleName}")
                }
            }
        }
        connection.launch {
            try {
                peripheral.observe(notify) { log("subscribed to fff1") }.collect { bytes ->
                    log("← ${bytes.toHex()}")
                    val current = session ?: newSession(model, bytes, settings).also { session = it }
                    for (event in current.onNotification(bytes)) {
                        runCatching { handle(event, outbox) }.onFailure { log("couldn't handle $event: ${it.message}") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("couldn't subscribe to fff1: ${e.message ?: e::class.simpleName}")
                throw e
            }
        }
        peripheral.state.first { it is State.Disconnected }
        log("disconnected")
    }

    private fun handle(event: ScaleEvent, outbox: Channel<ByteArray>) {
        when (event) {
            is ScaleEvent.Send -> outbox.trySend(event.bytes)
            is ScaleEvent.Weighing -> _status.value = ScaleStatus.Weighing(event.weightKg)
            is ScaleEvent.Reading -> {
                val saved = repo.add(
                    clock.now(), event.weightKg, event.impedanceOhms, event.impedanceHighOhms, tare.data.Source.SCALE,
                )
                _saved.tryEmit(saved)
                _status.value = ScaleStatus.Connected
                log("reading ${event.weightKg} kg, ${event.impedanceOhms ?: "no"} Ω")
            }
            is ScaleEvent.StoredReading -> {
                val at = Instant.fromEpochSeconds(event.takenAtUnixSeconds)
                repo.importStored(at, event.weightKg, event.impedanceOhms, event.impedanceHighOhms)
                    ?.let { _saved.tryEmit(it) }
                log("stored reading ${event.weightKg} kg at $at")
            }
            is ScaleEvent.Log -> log(event.message)
        }
    }

    /**
     * Picks the session from the paired model. For a device paired without one, its first frame
     * decides: the ESF-551 streams `a5 02` weight frames, the ESF-24 opens with a unit request.
     */
    private fun newSession(model: ScaleModel?, firstFrame: ByteArray, settings: tare.data.Settings): ScaleSession {
        val platform = model?.platform
            ?: if (Esf551Protocol.isFrame(firstFrame)) ScalePlatform.ETEKCITY else ScalePlatform.QN
        log("${model?.displayName ?: "unrecognised scale"}: ${platform.name.lowercase()} protocol")
        return when (platform) {
            ScalePlatform.ETEKCITY -> Esf551Session(settings.unit.scaleUnit, canSetUnit = model?.canSetUnit ?: true)
            ScalePlatform.QN -> Esf24Session(settings.unit.scaleUnit, settings.importOfflineReadings) { clock.now().epochSeconds }
        }
    }

    /** Reads the hardware and firmware revisions (device information service), as other ESF-551 clients do. */
    private suspend fun logVersions(peripheral: Peripheral) {
        for ((uuid, label) in listOf(HARDWARE_REVISION_UUID to "hardware", FIRMWARE_REVISION_UUID to "firmware")) {
            val characteristic = peripheral.characteristicFor(uuid) ?: continue
            try {
                val value = withTimeoutOrNull(3.seconds) { peripheral.read(characteristic) }
                log(if (value == null) "no reply reading $label revision" else "$label ${value.decodeToString().trim()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("couldn't read $label revision: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    private fun Peripheral.findCharacteristic(uuid: Uuid): DiscoveredCharacteristic? =
        services.value?.flatMap { it.characteristics }?.firstOrNull { it.characteristicUuid == uuid }

    /** A UUID reference to the discovered characteristic [uuid], in whichever service has it. */
    private fun Peripheral.characteristicFor(uuid: Uuid): Characteristic? =
        findCharacteristic(uuid)?.let { characteristicOf(it.serviceUuid, it.characteristicUuid) }

    /**
     * The ESF-551 is written without response, as its reverse-engineering did; the ESF-24 with
     * response unless its characteristic only allows the other kind.
     */
    private fun writeTypeFor(peripheral: Peripheral, platform: ScalePlatform): WriteType {
        val properties = peripheral.findCharacteristic(WRITE_UUID)?.properties
        return when {
            properties == null -> WriteType.WithResponse
            platform == ScalePlatform.ETEKCITY && properties.writeWithoutResponse -> WriteType.WithoutResponse
            !properties.write -> WriteType.WithoutResponse
            else -> WriteType.WithResponse
        }
    }

    private fun logServices(peripheral: Peripheral) {
        val services = peripheral.services.value ?: return log("no services discovered")
        log(
            "services " + services.joinToString("; ") { service ->
                service.serviceUuid.short() + " [" +
                    service.characteristics.joinToString(", ") { "${it.characteristicUuid.short()} ${it.properties.describe()}" } + "]"
            },
        )
    }

    private fun Characteristic.Properties.describe(): String =
        listOfNotNull(
            "read".takeIf { read },
            "write".takeIf { write },
            "write-no-response".takeIf { writeWithoutResponse },
            "notify".takeIf { notify },
            "indicate".takeIf { indicate },
        ).joinToString("/").ifEmpty { "-" }

    /** Bluetooth base UUIDs (0000xxxx-0000-1000-8000-00805f9b34fb) as their four hex digits. */
    private fun Uuid.short(): String {
        val s = toString()
        return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8) else s
    }

    private fun log(message: String) {
        val line = "${clock.now()}  $message"
        _log.value = (_log.value + line).takeLast(500)
        appendBluetoothLog(line)
    }

    private companion object {
        val SERVICE_UUID = Uuid.parse(Esf24Protocol.SERVICE_UUID)
        val NOTIFY_UUID = Uuid.parse(Esf24Protocol.NOTIFY_UUID)
        val WRITE_UUID = Uuid.parse(Esf24Protocol.WRITE_UUID)
        val NOTIFY = characteristicOf(SERVICE_UUID, NOTIFY_UUID)
        val WRITE = characteristicOf(SERVICE_UUID, WRITE_UUID)
        val HARDWARE_REVISION_UUID = Uuid.parse("00002a27-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_UUID = Uuid.parse("00002a28-0000-1000-8000-00805f9b34fb")
    }
}
