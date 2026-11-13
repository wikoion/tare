package tare.platform

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import tare.units.WeightUnit

/** Hands a file to the system share sheet. */
expect fun shareFile(fileName: String, contents: String)

/** Appends a line to the Bluetooth log file (Documents/bluetooth-log.txt on iOS), which outlives the session. */
expect fun appendBluetoothLog(line: String)

/** True when the user's region shows times as 14:00 rather than 2:00 PM. */
expect fun uses24HourClock(): Boolean

/** True when the user's region writes "26 Oct" rather than "Oct 26". */
expect fun dayBeforeMonth(): Boolean

/** The weight unit a new user most likely expects, from their region. */
expect fun regionalWeightUnit(): WeightUnit

/** Opens this app's page in the system Settings (to grant Bluetooth access). */
expect fun openAppSettings()

/** Rebuilds a peripheral from the identifier saved at pairing, or null if the system has forgotten it. */
expect fun knownPeripheral(identifier: String, builder: PeripheralBuilder.() -> Unit = {}): Peripheral?

expect val appVersion: String

enum class RadioState { Unknown, On, Off, Unauthorized, Unsupported }

/** The Bluetooth radio's state. Collecting it may show the system's Bluetooth permission prompt. */
expect fun bluetoothState(): kotlinx.coroutines.flow.Flow<RadioState>
