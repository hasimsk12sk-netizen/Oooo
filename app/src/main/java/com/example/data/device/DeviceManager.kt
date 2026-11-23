package com.example.data.device

import android.content.Context
import com.example.data.model.RegisteredDevice
import com.example.data.repository.DeviceAndUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface DeviceAuthorizationState {
    data object Authorized : DeviceAuthorizationState
    data class Blocked(
        val installationId: String,
        val deviceName: String,
        val deviceModel: String
    ) : DeviceAuthorizationState
}

data class ExtendedDeviceTelemetry(
    val modelCode: String = "",
    val productName: String = "",
    val cpuArch: String = "",
    val ramInfo: String = "",
    val totalRam: String = "",
    val availableRam: String = "",
    val screenResolution: String = "",
    val screenDensity: String = "",
    val connectionType: String = "",
    val installedAt: Long = 0L,
    val deviceFingerprint: String = "",
    val legacyInstallationId: String? = null
)

/**
 * Coordinates idempotent device registration, foreground-aware live heartbeat updates,
 * immediate auth/foreground status sync, and real-time device block enforcement.
 * Excludes emulators, Robolectric runners, and synthetic test devices from registering.
 */
class DeviceManager internal constructor(
    private val installationId: String,
    private val deviceName: String,
    private val deviceModel: String,
    private val manufacturer: String = "",
    private val brand: String = "",
    private val androidVersion: String = "",
    private val sdkVersion: Int = 0,
    private val deviceType: String = "",
    private val isGenuineDevice: Boolean = true,
    private val versionCodeProvider: () -> Long,
    private val versionNameProvider: () -> String,
    private val registerOrHeartbeatOp: suspend (
        id: String,
        name: String,
        model: String,
        manufacturer: String,
        brand: String,
        androidVersion: String,
        sdkVersion: Int,
        deviceType: String,
        versionCode: Long,
        versionName: String
    ) -> Result<RegisteredDevice>,
    private val fetchServerStatusOp: suspend (String) -> Result<RegisteredDevice?>,
    private val observeStatusOp: (String) -> Flow<RegisteredDevice?>,
    private val telemetryProvider: () -> ExtendedDeviceTelemetry = { ExtendedDeviceTelemetry() },
    private val extendedRegisterOp: (suspend (
        id: String,
        name: String,
        model: String,
        manufacturer: String,
        brand: String,
        androidVersion: String,
        sdkVersion: Int,
        deviceType: String,
        telemetry: ExtendedDeviceTelemetry,
        versionCode: Long,
        versionName: String
    ) -> Result<RegisteredDevice>)? = null
) {
    internal constructor(
        installationId: String,
        deviceName: String,
        deviceModel: String,
        manufacturer: String = "",
        versionCodeProvider: () -> Long,
        versionNameProvider: () -> String,
        registerOrHeartbeatOp: suspend (String, String, String, String, Long, String) -> Result<RegisteredDevice>,
        fetchServerStatusOp: suspend (String) -> Result<RegisteredDevice?>,
        observeStatusOp: (String) -> Flow<RegisteredDevice?>
    ) : this(
        installationId = installationId,
        deviceName = deviceName,
        deviceModel = deviceModel,
        manufacturer = manufacturer,
        brand = manufacturer,
        androidVersion = "14",
        sdkVersion = 34,
        deviceType = "Phone",
        isGenuineDevice = true,
        versionCodeProvider = versionCodeProvider,
        versionNameProvider = versionNameProvider,
        registerOrHeartbeatOp = { id, name, model, mfr, _, _, _, _, vCode, vName ->
            registerOrHeartbeatOp(id, name, model, mfr, vCode, vName)
        },
        fetchServerStatusOp = fetchServerStatusOp,
        observeStatusOp = observeStatusOp
    )

    constructor(
        context: Context,
        repository: DeviceAndUpdateRepository
    ) : this(
        installationId = DeviceIdentityProvider.getOrCreateInstallationId(context),
        deviceName = DeviceIdentityProvider.getDeviceName(context),
        deviceModel = DeviceIdentityProvider.getDeviceModel(),
        manufacturer = DeviceIdentityProvider.getManufacturer(),
        brand = DeviceIdentityProvider.getBrand(),
        androidVersion = DeviceIdentityProvider.getAndroidVersion(),
        sdkVersion = DeviceIdentityProvider.getSdkVersion(),
        deviceType = DeviceIdentityProvider.getDeviceType(context),
        isGenuineDevice = DeviceIdentityProvider.isGenuinePhysicalDevice() &&
            !DeviceIdentityProvider.isFakeOrEmulatorMetadata(
                installationId = DeviceIdentityProvider.getOrCreateInstallationId(context),
                deviceName = DeviceIdentityProvider.getDeviceName(context),
                deviceModel = DeviceIdentityProvider.getDeviceModel(),
                manufacturer = DeviceIdentityProvider.getManufacturer(),
                brand = DeviceIdentityProvider.getBrand()
            ),
        versionCodeProvider = { DeviceIdentityProvider.getInstalledVersionCode(context) },
        versionNameProvider = { DeviceIdentityProvider.getInstalledVersionName(context) },
        registerOrHeartbeatOp = { id, name, model, mfr, brand, androidVer, sdk, devType, vCode, vName ->
            repository.registerOrHeartbeatDevice(
                installationId = id,
                deviceName = name,
                deviceModel = model,
                manufacturer = mfr,
                brand = brand,
                androidVersion = androidVer,
                sdkVersion = sdk,
                deviceType = devType,
                versionCode = vCode,
                versionName = vName
            )
        },
        fetchServerStatusOp = { id ->
            repository.fetchDeviceRecordFromServer(id)
        },
        observeStatusOp = { id ->
            repository.observeDeviceRecord(id)
        },
        telemetryProvider = {
            val (totalRam, availableRam) = DeviceIdentityProvider.getMemorySummary(context)
            ExtendedDeviceTelemetry(
                modelCode = DeviceIdentityProvider.getModelCode(),
                productName = DeviceIdentityProvider.getProductName(),
                cpuArch = DeviceIdentityProvider.getCpuArch(),
                ramInfo = totalRam,
                totalRam = totalRam,
                availableRam = availableRam,
                screenResolution = DeviceIdentityProvider.getScreenResolution(context),
                screenDensity = DeviceIdentityProvider.getScreenDensity(context),
                connectionType = DeviceIdentityProvider.getConnectionType(context),
                installedAt = DeviceIdentityProvider.getInstalledAtMillis(context),
                deviceFingerprint = DeviceIdentityProvider.getDeviceFingerprint(context),
                legacyInstallationId = DeviceIdentityProvider.getLegacyInstallationId(context)
            )
        },
        extendedRegisterOp = { id, name, model, mfr, brand, androidVer, sdk, devType, telemetry, vCode, vName ->
            val result = repository.registerOrHeartbeatDevice(
                installationId = id,
                deviceName = name,
                deviceModel = model,
                manufacturer = mfr,
                brand = brand,
                androidVersion = androidVer,
                sdkVersion = sdk,
                deviceType = devType,
                versionCode = vCode,
                versionName = vName,
                modelCode = telemetry.modelCode,
                productName = telemetry.productName,
                cpuArch = telemetry.cpuArch,
                ramInfo = telemetry.ramInfo,
                totalRam = telemetry.totalRam,
                availableRam = telemetry.availableRam,
                screenResolution = telemetry.screenResolution,
                screenDensity = telemetry.screenDensity,
                connectionType = telemetry.connectionType,
                installedAt = telemetry.installedAt,
                deviceFingerprint = telemetry.deviceFingerprint,
                legacyInstallationId = telemetry.legacyInstallationId
            )
            if (result.isSuccess && telemetry.legacyInstallationId != null) {
                DeviceIdentityProvider.clearLegacyInstallationId(context)
            }
            result
        }
    )

    private val _authState = MutableStateFlow<DeviceAuthorizationState>(DeviceAuthorizationState.Authorized)
    val authState: StateFlow<DeviceAuthorizationState> = _authState.asStateFlow()

    private val _isRefreshingStatus = MutableStateFlow(false)
    val isRefreshingStatus: StateFlow<Boolean> = _isRefreshingStatus.asStateFlow()

    private var observerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var activeScope: CoroutineScope? = null
    private val lastSyncEpochMs = AtomicLong(0L)
    private val isInForeground = AtomicBoolean(true)

    fun start(scope: CoroutineScope) {
        if (!isGenuineDevice) {
            return
        }
        // Prevent redundant duplicate listener / registration loops if already running for this scope
        if (activeScope === scope && observerJob?.isActive == true && heartbeatJob?.isActive == true) {
            return
        }

        observerJob?.cancel()
        heartbeatJob?.cancel()
        activeScope = scope
        isInForeground.set(true)

        // 1. Idempotent initial registration or heartbeat on startup (Guest or Gmail)
        lastSyncEpochMs.set(System.currentTimeMillis())
        scope.launch(Dispatchers.IO) {
            val result = performRegisterOrHeartbeat()
            result.getOrNull()?.let { record ->
                applyDeviceRecord(record)
            }
        }

        // 2. Single real-time Firestore snapshot listener on this installation's document
        observerJob = scope.launch {
            observeStatusOp(installationId).collect { record ->
                if (record != null) {
                    applyDeviceRecord(record)
                }
            }
        }

        // 3. Periodic server-verified heartbeat & authorization check (active only while in foreground)
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isInForeground.get()) {
                    continue
                }
                lastSyncEpochMs.set(System.currentTimeMillis())
                val serverCheck = fetchServerStatusOp(installationId).getOrNull()
                if (serverCheck != null) {
                    applyDeviceRecord(serverCheck)
                    if (!serverCheck.isBlocked) {
                        performRegisterOrHeartbeat()
                    }
                } else {
                    performRegisterOrHeartbeat()
                }
            }
        }
    }

    fun onForeground(scope: CoroutineScope) {
        isInForeground.set(true)
        checkNow(scope, force = false)
    }

    fun onBackground() {
        isInForeground.set(false)
    }

    fun checkNow(scope: CoroutineScope, force: Boolean = false) {
        if (!isGenuineDevice) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncEpochMs.get()) < CHECK_DEBOUNCE_MS) {
            return
        }
        lastSyncEpochMs.set(now)

        scope.launch(Dispatchers.IO) {
            _isRefreshingStatus.value = true
            try {
                val serverRecord = fetchServerStatusOp(installationId).getOrNull()
                if (serverRecord != null) {
                    applyDeviceRecord(serverRecord)
                    if (!serverRecord.isBlocked) {
                        performRegisterOrHeartbeat()
                    }
                } else {
                    val regResult = performRegisterOrHeartbeat().getOrNull()
                    if (regResult != null) {
                        applyDeviceRecord(regResult)
                    }
                }
            } finally {
                _isRefreshingStatus.value = false
            }
        }
    }

    private suspend fun performRegisterOrHeartbeat(): Result<RegisteredDevice> {
        val code = versionCodeProvider()
        val vName = versionNameProvider()
        val ext = extendedRegisterOp
        return if (ext != null) {
            ext(
                installationId,
                deviceName,
                deviceModel,
                manufacturer,
                brand,
                androidVersion,
                sdkVersion,
                deviceType,
                telemetryProvider(),
                code,
                vName
            )
        } else {
            registerOrHeartbeatOp(
                installationId,
                deviceName,
                deviceModel,
                manufacturer,
                brand,
                androidVersion,
                sdkVersion,
                deviceType,
                code,
                vName
            )
        }
    }

    fun stop() {
        isInForeground.set(false)
        observerJob?.cancel()
        heartbeatJob?.cancel()
        observerJob = null
        heartbeatJob = null
        activeScope = null
    }

    private fun applyDeviceRecord(record: RegisteredDevice) {
        _authState.value = if (record.isBlocked) {
            DeviceAuthorizationState.Blocked(
                installationId = record.installationId,
                deviceName = record.deviceName,
                deviceModel = record.deviceModel
            )
        } else {
            DeviceAuthorizationState.Authorized
        }
    }

    companion object {
        internal const val HEARTBEAT_INTERVAL_MS = 45_000L
        private const val CHECK_DEBOUNCE_MS = 3_000L
    }
}
