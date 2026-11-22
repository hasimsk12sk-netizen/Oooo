package com.example.data.device

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.model.RegisteredDevice
import java.util.UUID

/**
 * Provides a privacy-safe, app-scoped installation UUID, real non-sensitive device metadata,
 * and reliable detection of emulators, Robolectric runners, and synthetic test environments.
 * Never reads IMEI, phone numbers, hardware serial numbers, MAC addresses, or location.
 */
object DeviceIdentityProvider {

    private const val PREFS_NAME = "storepro_device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val NOT_AVAILABLE = RegisteredDevice.NOT_AVAILABLE

    fun getOrCreateInstallationId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)?.trim()
        if (!existing.isNullOrBlank() && existing.matches(Regex("^[a-zA-Z0-9_\\-]{8,128}$"))) {
            return existing
        }
        val generated = "sp_${UUID.randomUUID()}"
        prefs.edit { putString(KEY_INSTALLATION_ID, generated) }
        return generated
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
}
