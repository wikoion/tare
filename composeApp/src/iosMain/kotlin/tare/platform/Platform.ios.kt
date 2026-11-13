package tare.platform

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.toIdentifier
import platform.Foundation.NSBundle
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleMeasurementSystem
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.currentLocale
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.Foundation.closeFile
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import tare.units.WeightUnit

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun shareFile(fileName: String, contents: String) {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
    (contents as NSString).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    val top = topViewController() ?: return
    val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    top.presentViewController(sheet, animated = true, completion = null)
}

private const val LOG_FILE_LIMIT = 2_000_000uL

/** Keeps the current file and one older one of up to [LOG_FILE_LIMIT] bytes each. */
@Suppress("CAST_NEVER_SUCCEEDS")
actual fun appendBluetoothLog(line: String) {
    val dir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return
    val path = "$dir/bluetooth-log.txt"
    val data = ("$line\n" as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
    val files = NSFileManager.defaultManager
    val handle = NSFileHandle.fileHandleForWritingAtPath(path)
    if (handle == null) {
        files.createFileAtPath(path, data, null)
        return
    }
    val size = handle.seekToEndOfFile()
    if (size > LOG_FILE_LIMIT) {
        handle.closeFile()
        val older = "$dir/bluetooth-log.1.txt"
        files.removeItemAtPath(older, null)
        files.moveItemAtPath(path, older, null)
        files.createFileAtPath(path, data, null)
        return
    }
    handle.writeData(data)
    handle.closeFile()
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var vc = window?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

actual fun uses24HourClock(): Boolean {
    val format = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale) ?: return false
    return !format.contains('a')
}

actual fun dayBeforeMonth(): Boolean {
    val format = NSDateFormatter.dateFormatFromTemplate("MMMd", 0u, NSLocale.currentLocale) ?: return false
    return format.indexOf('d') < format.indexOf('M')
}

actual fun regionalWeightUnit(): WeightUnit =
    when (NSLocale.currentLocale.objectForKey(NSLocaleMeasurementSystem) as? String) {
        "U.S." -> WeightUnit.LB
        "U.K." -> WeightUnit.ST
        else -> WeightUnit.KG
    }

actual fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun knownPeripheral(identifier: String, builder: PeripheralBuilder.() -> Unit): Peripheral? =
    runCatching { Peripheral(identifier.toIdentifier(), builder) }.getOrNull()

actual val appVersion: String
    get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "dev"
