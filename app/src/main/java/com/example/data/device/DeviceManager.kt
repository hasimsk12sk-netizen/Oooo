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
import java.util.concurrent.atomic.AtomicLong

sealed interface DeviceAuthorizationState {
    data object Authorized : DeviceAuthorizationState
    data class Blocked(
        val installationId: String,
        val deviceName: String,
        val deviceModel: String
    ) : DeviceAuthorizationState
}

/**
 * Coordinates unique installation registration, periodic foreground heartbeat updates,
 * and real-time + server-checked device block enforcement.
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
    private val observeStatusOp: (String) -> Flow<RegisteredDevice?>
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
        }
    )

    private val _authState = MutableStateFlow<DeviceAuthorizationState>(DeviceAuthorizationState.Authorized)
    val authState: StateFlow<DeviceAuthorizationState> = _authState.asStateFlow()

    private val _isRefreshingStatus = MutableStateFlow(false)
    val isRefreshingStatus: StateFlow<Boolean> = _isRefreshingStatus.asStateFlow()

    private var observerJob: Job? = null
    private var heartbeatJob: Job? = null
    private val lastSyncEpochMs = AtomicLong(0L)

    fun start(scope: CoroutineScope) {
        observerJob?.cancel()
        heartbeatJob?.cancel()

        if (!isGenuineDevice) {
            return
        }

        // 1. Register or refresh heartbeat on startup (independent of Gmail login)
        lastSyncEpochMs.set(System.currentTimeMillis())
        scope.launch(Dispatchers.IO) {
            val result = registerOrHeartbeatOp(
                installationId,
                deviceName,
                deviceModel,
                manufacturer,
                brand,
                androidVersion,
                sdkVersion,
                deviceType,
                versionCodeProvider(),
                versionNameProvider()
            )
            result.getOrNull()?.let { record ->
                applyDeviceRecord(record)
            }
        }

        // 2. Real-time Firestore listener on this installation's document
        observerJob = scope.launch {
            observeStatusOp(installationId).collect { record ->
                if (record != null) {
                    applyDeviceRecord(record)
                }
            }
        }

        // 3. Periodic server-verified heartbeat & authorization check
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                lastSyncEpochMs.set(System.currentTimeMillis())
                val serverCheck = fetchServerStatusOp(installationId).getOrNull()
                if (serverCheck != null) {
                    applyDeviceRecord(serverCheck)
                    if (!serverCheck.isBlocked) {
                        registerOrHeartbeatOp(
                            installationId,
                            deviceName,
                            deviceModel,
                            manufacturer,
                            brand,
                            androidVersion,
                            sdkVersion,
                            deviceType,
                            versionCodeProvider(),
                            versionNameProvider()
                        )
                    }
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
                        versionCodeProvider(),
                        versionNameProvider()
                    )
                }
            }
        }
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
                        registerOrHeartbeatOp(
                            installationId,
                            deviceName,
                            deviceModel,
                            manufacturer,
                            brand,
                            androidVersion,
                            sdkVersion,
                            deviceType,
                            versionCodeProvider(),
                            versionNameProvider()
                        )
                    }
                } else {
                    val regResult = registerOrHeartbeatOp(
                        installationId,
                        deviceName,
                        deviceModel,
                        manufacturer,
                        brand,
                        androidVersion,
                        sdkVersion,
                        deviceType,
                        versionCodeProvider(),
                        versionNameProvider()
                    ).getOrNull()
                    if (regResult != null) {
                        applyDeviceRecord(regResult)
                    }
                }
            } finally {
                _isRefreshingStatus.value = false
            }
        }
    }

    fun stop() {
        observerJob?.cancel()
        heartbeatJob?.cancel()
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
        private const val CHECK_DEBOUNCE_MS = 4_000L
    }
}
