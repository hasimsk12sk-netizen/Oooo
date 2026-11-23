package com.example.data.device

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.model.RegisteredDevice
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Provides a stable, privacy-safe, app-scoped installation identifier and real non-sensitive
 * hardware/OS/display/RAM/install metadata.
 * Uses Settings.Secure.ANDROID_ID (scoped by Android per device + user + signing key, hashed via SHA-256)
 * so one physical device keeps one canonical ID across restarts, Gmail/Guest switches, and reinstalls
 * while two different physical phones of the same model always stay distinct.
 */
object DeviceIdentityProvider {

    private const val PREFS_NAME = "storepro_device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_LEGACY_INSTALLATION_ID = "legacy_installation_id"
    private const val NOT_AVAILABLE = RegisteredDevice.NOT_AVAILABLE
    private const val LEGACY_KNOWN_BROKEN_ANDROID_ID = "9774d56d682e549c"
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{8,128}$")

    fun getOrCreateInstallationId(context: Context): String {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingPref = prefs.getString(KEY_INSTALLATION_ID, null)?.trim()

        val androidId = readValidAndroidId(appContext)
        if (androidId != null && isGenuinePhysicalDevice()) {
            val canonical = computeCanonicalInstallationId(appContext.packageName, androidId)
            if (existingPref != canonical) {
                prefs.edit {
                    if (!existingPref.isNullOrBlank() &&
                        existingPref.matches(VALID_ID_REGEX) &&
                        existingPref != canonical
                    ) {
                        putString(KEY_LEGACY_INSTALLATION_ID, existingPref)
                    }
                    putString(KEY_INSTALLATION_ID, canonical)
                }
            }
            return canonical
        }

        if (!existingPref.isNullOrBlank() && existingPref.matches(VALID_ID_REGEX)) {
            return existingPref
        }
        val generated = "sp_${UUID.randomUUID()}"
        prefs.edit { putString(KEY_INSTALLATION_ID, generated) }
        return generated
    }

    fun getLegacyInstallationId(context: Context): String? {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_INSTALLATION_ID, null)?.trim()
        val legacy = prefs.getString(KEY_LEGACY_INSTALLATION_ID, null)?.trim()
        return legacy?.takeIf {
            it.isNotBlank() && it != current && it.matches(VALID_ID_REGEX)
        }
    }

    fun clearLegacyInstallationId(context: Context) {
        val appContext = context.applicationContext ?: context
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_LEGACY_INSTALLATION_ID)
        }
    }

    fun getDeviceFingerprint(context: Context): String {
        val appContext = context.applicationContext ?: context
        if (!isGenuinePhysicalDevice()) return ""
        val androidId = readValidAndroidId(appContext) ?: return ""
        return computeDeviceFingerprint(appContext.packageName, androidId)
    }

    internal fun computeCanonicalInstallationId(packageName: String, androidId: String): String {
        return "sp_${sha256Hex("storepro_install_v1:$packageName:$androidId").take(32)}"
    }

    internal fun computeDeviceFingerprint(packageName: String, androidId: String): String {
        return sha256Hex("storepro_fp_v1:$packageName:$androidId")
    }

    internal fun readValidAndroidId(context: Context): String? {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()
        if (raw.length < 8) return null
        if (raw.equals(LEGACY_KNOWN_BROKEN_ANDROID_ID, ignoreCase = true)) return null
        if (raw.equals("unknown", ignoreCase = true) || raw.equals("emulator", ignoreCase = true)) return null
        if (raw.all { it == '0' }) return null
        return raw
    }

    fun getDeviceName(context: Context): String {
        val globalName = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()?.trim()?.takeIf { it.isValidHardwareString() }

        val brandOrMfr = (Build.BRAND ?: "").trim().takeIf { it.isValidHardwareString() }
            ?: (Build.MANUFACTURER ?: "").trim().takeIf { it.isValidHardwareString() }
        val model = (Build.MODEL ?: "").trim().takeIf { it.isValidHardwareString() }

        val candidate = when {
            globalName != null -> globalName
            brandOrMfr != null && model != null -> {
                if (model.startsWith(brandOrMfr, ignoreCase = true)) model
                else "${brandOrMfr.replaceFirstChar { it.uppercase() }} $model"
            }
            model != null -> model
            else -> NOT_AVAILABLE
        }
        return candidate.trim().take(120).ifBlank { NOT_AVAILABLE }
    }

    fun getManufacturer(): String {
        val raw = (Build.MANUFACTURER ?: "").trim()
        if (!raw.isValidHardwareString()) return NOT_AVAILABLE
        return raw.replaceFirstChar { it.uppercase() }.take(80)
    }

    fun getBrand(): String {
        val raw = (Build.BRAND ?: "").trim()
        if (!raw.isValidHardwareString()) return NOT_AVAILABLE
        return raw.replaceFirstChar { it.uppercase() }.take(80)
    }

    fun getDeviceModel(): String {
        val raw = (Build.MODEL ?: "").trim()
        if (!raw.isValidHardwareString()) return NOT_AVAILABLE
        return raw.take(120)
    }

    fun getModelCode(): String {
        val model = (Build.MODEL ?: "").trim().takeIf { it.isValidHardwareString() }
        val sku = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (Build.SKU ?: "").trim().takeIf { it.isValidHardwareString() }
        } else {
            null
        }
        return when {
            model != null && sku != null && !sku.equals(model, ignoreCase = true) ->
                "$model ($sku)".take(80)
            model != null -> model.take(80)
            sku != null -> sku.take(80)
            else -> NOT_AVAILABLE
        }
    }

    fun getProductName(): String {
        val raw = (Build.PRODUCT ?: "").trim()
        if (!raw.isValidHardwareString()) return NOT_AVAILABLE
        return raw.take(80)
    }

    fun getAndroidVersion(): String {
        val raw = (Build.VERSION.RELEASE ?: "").trim()
        if (!raw.isValidHardwareString()) return NOT_AVAILABLE
        return raw.take(40)
    }

    fun getSdkVersion(): Int {
        return Build.VERSION.SDK_INT.coerceAtLeast(0)
    }

    fun getDeviceType(context: Context): String {
        val config = runCatching { context.resources.configuration }.getOrNull() ?: return NOT_AVAILABLE
        val screenLayoutMask = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val isLargeScreen = screenLayoutMask >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isWideSmallestDp = config.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED &&
            config.smallestScreenWidthDp >= 600
        return if (isLargeScreen || isWideSmallestDp) "Tablet" else "Phone"
    }

    fun getCpuArch(): String {
        val abi = Build.SUPPORTED_ABIS?.firstOrNull()?.trim().orEmpty()
        if (!abi.isValidHardwareString()) return NOT_AVAILABLE
        return abi.take(60)
    }

    fun getRamInfo(context: Context): String {
        return getMemorySummary(context).first
    }

    fun getAvailableRamInfo(context: Context): String {
        return getMemorySummary(context).second
    }

    fun getMemorySummary(context: Context): Pair<String, String> {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return@runCatching (NOT_AVAILABLE to NOT_AVAILABLE)
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalStr = formatMemoryBytes(memInfo.totalMem)
            val availStr = formatMemoryBytes(memInfo.availMem)
            totalStr to availStr
        }.getOrDefault(NOT_AVAILABLE to NOT_AVAILABLE)
    }

    internal fun formatMemoryBytes(bytes: Long): String {
        if (bytes <= 0L) return NOT_AVAILABLE
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            val mb = bytes / (1024L * 1024L)
            if (mb > 0L) "$mb MB" else NOT_AVAILABLE
        }
    }

    @Suppress("DEPRECATION")
    fun getScreenResolution(context: Context): String {
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.maximumWindowMetrics.bounds
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        return@runCatching "${bounds.width()} x ${bounds.height()}"
                    }
                } else {
                    val realDm = DisplayMetrics()
                    wm.defaultDisplay?.getRealMetrics(realDm)
                    if (realDm.widthPixels > 0 && realDm.heightPixels > 0) {
                        return@runCatching "${realDm.widthPixels} x ${realDm.heightPixels}"
                    }
                }
            }
            val dm = context.resources.displayMetrics
            if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                "${dm.widthPixels} x ${dm.heightPixels}"
            } else {
                NOT_AVAILABLE
            }
        }.getOrDefault(NOT_AVAILABLE)
    }

    fun getScreenDensity(context: Context): String {
        return runCatching {
            val dpi = context.resources.displayMetrics.densityDpi
            if (dpi > 0) "$dpi dpi" else NOT_AVAILABLE
        }.getOrDefault(NOT_AVAILABLE)
    }

    fun getConnectionType(context: Context): String {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching "Offline"
            val active = cm.activeNetwork ?: return@runCatching "Offline"
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Online"
            }
        }.getOrDefault(NOT_AVAILABLE)
    }

    fun getInstalledAtMillis(context: Context): Long {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime.takeIf { it > 0L } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Returns true ONLY when running on a genuine physical Android device (never Robolectric or SDK emulators).
     */
    fun isGenuinePhysicalDevice(): Boolean {
        return !isRobolectricRuntime() && !isEmulatorBuildProperties(
            fingerprint = Build.FINGERPRINT.orEmpty(),
            model = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            host = Build.HOST.orEmpty()
        )
    }

    internal fun isRobolectricRuntime(): Boolean {
        return runCatching {
            Class.forName("org.robolectric.Robolectric")
            true
        }.getOrDefault(false)
    }

    internal fun isEmulatorBuildProperties(
        fingerprint: String,
        model: String,
        manufacturer: String,
        brand: String,
        device: String,
        product: String,
        hardware: String,
        board: String,
        host: String
    ): Boolean {
        val fp = fingerprint.lowercase()
        val m = model.lowercase()
        val mfr = manufacturer.lowercase()
        val b = brand.lowercase()
        val d = device.lowercase()
        val p = product.lowercase()
        val hw = hardware.lowercase()
        val brd = board.lowercase()
        val hst = host.lowercase()

        if (fp == "robolectric" || fp.contains("robolectric") || hst.contains("robolectric")) {
            return true
        }
        if (fp.startsWith("generic") || fp.startsWith("unknown") || fp.contains("emulator")) {
            return true
        }
        if (m.contains("google_sdk") ||
            m.contains("emulator") ||
            m.contains("android sdk built for") ||
            m.contains("sdk_gphone") ||
            m == "droid4x"
        ) {
            return true
        }
        if (mfr.contains("genymotion") || mfr == "unknown" && m == "unknown") {
            return true
        }
        if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox86") || hw.contains("qemu")) {
            return true
        }
        if (p.contains("sdk_gphone") ||
            p.contains("sdk_google") ||
            p.contains("google_sdk") ||
            p == "sdk" ||
            p == "sdk_x86" ||
            p.contains("emulator") ||
            p.contains("simulator") ||
            p.contains("vbox86p")
        ) {
            return true
        }
        if (b.startsWith("generic") && d.startsWith("generic")) {
            return true
        }
        if (d.startsWith("emu64") || d.startsWith("generic_x86") || brd.contains("goldfish") || brd.contains("ranchu")) {
            return true
        }
        return false
    }

    fun isFakeOrEmulatorMetadata(
        installationId: String,
        deviceName: String,
        deviceModel: String,
        manufacturer: String,
        brand: String
    ): Boolean {
        return !RegisteredDevice(
            installationId = installationId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            manufacturer = manufacturer,
            brand = brand
        ).isGenuineDeviceRecord()
    }

    fun getInstalledVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info)
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE.toLong().coerceAtLeast(1L)
        }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName?.trim()?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME.ifBlank { "1.0" }
        }
    }

    private fun String.isValidHardwareString(): Boolean {
        return isNotBlank() && !equals("unknown", ignoreCase = true)
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
