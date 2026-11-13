package tare.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

private class StateDelegate(private val onState: (RadioState) -> Unit) : NSObject(), CBCentralManagerDelegateProtocol {
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        onState(
            when (central.state) {
                CBManagerStatePoweredOn -> RadioState.On
                CBManagerStatePoweredOff -> RadioState.Off
                CBManagerStateUnauthorized -> RadioState.Unauthorized
                CBManagerStateUnsupported -> RadioState.Unsupported
                else -> RadioState.Unknown
            },
        )
    }
}

actual fun bluetoothState(): Flow<RadioState> = callbackFlow {
    val delegate = StateDelegate { trySend(it) }
    val manager = CBCentralManager(
        delegate = delegate,
        queue = dispatch_get_main_queue(),
        options = mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to false),
    )
    awaitClose { manager.delegate = null }
}.distinctUntilChanged()
