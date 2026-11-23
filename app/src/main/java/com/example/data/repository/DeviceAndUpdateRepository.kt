package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.device.DeviceIdentityProvider
import com.example.data.model.ApplicationItem
import com.example.data.model.RegisteredDevice
import com.example.ui.admin.AdminAuthManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DeviceAndUpdateRepository(
    private val context: Context,
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireAdminUserId(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Administrator Google Sign-In is required.")
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
            throw SecurityException("Access Denied: Verified administrator authorization required.")
        }
        return user.uid
    }

    // ------------------------------------------------------------
    // 1. Server-Side Admin Role Verification
    // ------------------------------------------------------------

    suspend fun syncAdminRoleIfAuthorized(): Result<Boolean> {
        val user = auth.currentUser ?: return Result.success(false)
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
            return Result.success(false)
        }
        val docRef = db.collection("admin_roles").document(user.uid)
        return try {
            val payload = mapOf(
                "uid" to user.uid,
                "email" to (user.email ?: AdminAuthManager.AUTHORIZED_ADMIN_EMAIL).take(200),
                "role" to "admin",
                "active" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun verifyServerAdminAuthorization(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val snap = try {
                db.collection("admin_roles").document(user.uid).get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection("admin_roles").document(user.uid).get().await()
            }
            if (!snap.exists()) {
                if (AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
                    syncAdminRoleIfAuthorized().getOrDefault(false)
                } else {
                    false
                }
            } else {
                snap.getBoolean("active") == true && snap.getString("role") == "admin"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------
    // 2. Live Device Registration, Heartbeat & Blocking
    // ------------------------------------------------------------

    suspend fun registerOrHeartbeatDevice(
        installationId: String,
        deviceName: String,
        deviceModel: String,
        manufacturer: String = "",
        brand: String = "",
        androidVersion: String = "",
        sdkVersion: Int = 0,
        deviceType: String = "",
        versionCode: Long,
        versionName: String,
        modelCode: String = "",
        productName: String = "",
        cpuArch: String = "",
        ramInfo: String = "",
        totalRam: String = "",
        availableRam: String = "",
        screenResolution: String = "",
        screenDensity: String = "",
        connectionType: String = "",
        installedAt: Long = 0L,
        deviceFingerprint: String = "",
        legacyInstallationId: String? = null
    ): Result<RegisteredDevice> {
        if (installationId.isBlank()) {
            return Result.failure(IllegalArgumentException("Installation ID cannot be blank"))
        }
        if (!DeviceIdentityProvider.isGenuinePhysicalDevice() ||
            DeviceIdentityProvider.isFakeOrEmulatorMetadata(
                installationId = installationId,
                deviceName = deviceName,
                deviceModel = deviceModel,
                manufacturer = manufacturer,
                brand = brand
            )
        ) {
            return Result.failure(IllegalStateException("Emulator or synthetic test device excluded from registry"))
        }

        val docRef = db.collection("registered_devices").document(installationId)
        val safeName = deviceName.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }.take(120)
        val safeModel = deviceModel.trim().ifBlank { RegisteredDevice.NOT_AVAILABLE }.take(120)
        val safeManufacturer = sanitizeOptionalField(manufacturer, 80)
        val safeBrand = sanitizeOptionalField(brand, 80)
        val safeModelCode = sanitizeOptionalField(modelCode, 80)
        val safeProductName = sanitizeOptionalField(productName, 80)
        val safeCpuArch = sanitizeOptionalField(cpuArch, 60)
        val resolvedTotalRam = sanitizeOptionalField(totalRam.ifBlank { ramInfo }, 60)
        val safeRamInfo = sanitizeOptionalField(ramInfo.ifBlank { resolvedTotalRam }, 60)
        val safeAvailableRam = sanitizeOptionalField(availableRam, 60)
        val safeResolution = sanitizeOptionalField(screenResolution, 40)
        val safeDensity = sanitizeOptionalField(screenDensity, 40)
        val safeConnectionType = sanitizeOptionalField(connectionType, 40)
        val safeAndroidVersion = sanitizeOptionalField(androidVersion, 40)
        val safeSdkVersion = sdkVersion.coerceIn(0, 200)
        val safeDeviceType = sanitizeOptionalField(deviceType, 40)
        val safeVersionCode = versionCode.coerceAtLeast(1L)
        val safeVersionName = versionName.trim().ifBlank { "1.0" }.take(40)
        val safeInstalledAt = installedAt.coerceAtLeast(0L)
        val safeFingerprint = sanitizeOptionalField(deviceFingerprint, 128)
        val safeLegacyId = legacyInstallationId?.trim()
            ?.takeIf { it.isNotBlank() && it != installationId && it.length <= 128 }
        val currentUser = auth.currentUser
        val accountStatus = if (currentUser != null) {
            RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
        } else {
            RegisteredDevice.ACCOUNT_STATUS_GUEST
        }
        val associatedUid = currentUser?.uid?.take(128)
        val associatedEmail = currentUser?.email?.take(200)

        return try {
            val existingSnap = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get().await()
            }

            if (!existingSnap.exists()) {
                // If upgrading from a legacy random UUID on the SAME installation, carry forward earliest timestamps & block state
                val legacySnap = if (safeLegacyId != null) {
                    runCatching {
                        db.collection("registered_devices").document(safeLegacyId).get().await()
                    }.getOrNull()?.takeIf { it.exists() }
                } else {
                    null
                }
                val legacyRecord = legacySnap?.let { runCatching { it.toRegisteredDeviceSafe() }.getOrNull() }
                val legacyFirstTs = legacyRecord?.effectiveFirstUsedAt ?: 0L
                val preservedRegisteredTs = legacySnap?.get("registeredAt")
                val preservedFirstUsedTs = legacySnap?.get("firstUsedAt") ?: preservedRegisteredTs
                val initialStatus = if (legacyRecord?.isBlocked == true) {
                    RegisteredDevice.STATUS_BLOCKED
                } else {
                    RegisteredDevice.STATUS_ACTIVE
                }

                val createPayload = mutableMapOf<String, Any?>(
                    "installationId" to installationId,
                    "deviceName" to safeName,
                    "deviceModel" to safeModel,
                    "manufacturer" to safeManufacturer,
                    "brand" to safeBrand,
                    "modelCode" to safeModelCode,
                    "productName" to safeProductName,
                    "cpuArch" to safeCpuArch,
                    "ramInfo" to safeRamInfo,
                    "totalRam" to resolvedTotalRam,
                    "availableRam" to safeAvailableRam,
                    "screenResolution" to safeResolution,
                    "screenDensity" to safeDensity,
                    "connectionType" to safeConnectionType,
                    "androidVersion" to safeAndroidVersion,
                    "sdkVersion" to safeSdkVersion,
                    "deviceType" to safeDeviceType,
                    "accountStatus" to accountStatus,
                    "associatedUserId" to associatedUid,
                    "associatedEmail" to associatedEmail,
                    "status" to initialStatus,
                    "appVersionCode" to safeVersionCode,
                    "appVersionName" to safeVersionName,
                    "installedAt" to safeInstalledAt,
                    "deviceFingerprint" to safeFingerprint,
                    "legacyInstallationId" to safeLegacyId,
                    "registeredAt" to (preservedRegisteredTs ?: FieldValue.serverTimestamp()),
                    "firstUsedAt" to (preservedFirstUsedTs ?: FieldValue.serverTimestamp()),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "lastSyncAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
                val now = System.currentTimeMillis()
                val created = RegisteredDevice(
                    installationId = installationId,
                    deviceName = safeName,
                    deviceModel = safeModel,
                    manufacturer = safeManufacturer,
                    brand = safeBrand,
                    modelCode = safeModelCode,
                    productName = safeProductName,
                    cpuArch = safeCpuArch,
                    ramInfo = safeRamInfo,
                    totalRam = resolvedTotalRam,
                    availableRam = safeAvailableRam,
                    screenResolution = safeResolution,
                    screenDensity = safeDensity,
                    connectionType = safeConnectionType,
                    androidVersion = safeAndroidVersion,
                    sdkVersion = safeSdkVersion,
                    deviceType = safeDeviceType,
                    accountStatus = accountStatus,
                    associatedUserId = associatedUid,
                    associatedEmail = associatedEmail,
                    status = initialStatus,
                    appVersionCode = safeVersionCode,
                    appVersionName = safeVersionName,
                    installedAt = safeInstalledAt,
                    registeredAt = legacyRecord?.registeredAt?.takeIf { it > 0L } ?: now,
                    firstUsedAt = if (legacyFirstTs > 0L) legacyFirstTs else now,
                    lastActiveAt = now,
                    lastSyncAt = now,
                    deviceFingerprint = safeFingerprint,
                    legacyInstallationId = safeLegacyId
                )
                Result.success(created)
            } else {
                val current = existingSnap.toRegisteredDeviceSafe()
                if (current.isBlocked) {
                    Result.success(current)
                } else {
                    val updates = mutableMapOf<String, Any?>(
                        "deviceName" to safeName,
                        "deviceModel" to safeModel,
                        "manufacturer" to safeManufacturer,
                        "brand" to safeBrand,
                        "modelCode" to safeModelCode,
                        "productName" to safeProductName,
                        "cpuArch" to safeCpuArch,
                        "ramInfo" to safeRamInfo,
                        "totalRam" to resolvedTotalRam,
                        "availableRam" to safeAvailableRam,
                        "screenResolution" to safeResolution,
                        "screenDensity" to safeDensity,
                        "connectionType" to safeConnectionType,
                        "androidVersion" to safeAndroidVersion,
                        "sdkVersion" to safeSdkVersion,
                        "deviceType" to safeDeviceType,
                        "accountStatus" to accountStatus,
                        "associatedUserId" to associatedUid,
                        "associatedEmail" to associatedEmail,
                        "appVersionCode" to safeVersionCode,
                        "appVersionName" to safeVersionName,
                        "installedAt" to safeInstalledAt,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "lastSyncAt" to FieldValue.serverTimestamp()
                    )
                    if (current.deviceFingerprint.isBlank() && safeFingerprint.isNotBlank()) {
                        updates["deviceFingerprint"] = safeFingerprint
                    }
                    if (current.legacyInstallationId.isNullOrBlank() && safeLegacyId != null) {
                        updates["legacyInstallationId"] = safeLegacyId
                    }
                    docRef.update(updates).await()
                    val now = System.currentTimeMillis()
                    Result.success(
                        current.copy(
                            deviceName = safeName,
                            deviceModel = safeModel,
                            manufacturer = safeManufacturer,
                            brand = safeBrand,
                            modelCode = safeModelCode,
                            productName = safeProductName,
                            cpuArch = safeCpuArch,
                            ramInfo = safeRamInfo,
                            totalRam = resolvedTotalRam,
                            availableRam = safeAvailableRam,
                            screenResolution = safeResolution,
                            screenDensity = safeDensity,
                            connectionType = safeConnectionType,
                            androidVersion = safeAndroidVersion,
                            sdkVersion = safeSdkVersion,
                            deviceType = safeDeviceType,
                            accountStatus = accountStatus,
                            associatedUserId = associatedUid,
                            associatedEmail = associatedEmail,
                            appVersionCode = safeVersionCode,
                            appVersionName = safeVersionName,
                            installedAt = safeInstalledAt,
                            lastActiveAt = now,
                            lastSyncAt = now,
                            deviceFingerprint = current.deviceFingerprint.ifBlank { safeFingerprint },
                            legacyInstallationId = current.legacyInstallationId ?: safeLegacyId
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchDeviceRecordFromServer(installationId: String): Result<RegisteredDevice?> {
        if (installationId.isBlank()) return Result.success(null)
        val docRef = db.collection("registered_devices").document(installationId)
        return try {
            val snap = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get().await()
            }
            Result.success(if (!snap.exists()) null else snap.toRegisteredDeviceSafe())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.READ, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDeviceRecord(installationId: String): Flow<RegisteredDevice?> = flow {
        if (installationId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "registered_devices/$installationId"
        try {
            emitAll(
                db.collection("registered_devices").document(installationId)
                    .snapshots()
                    .map { snap ->
                        if (!snap.exists()) null else snap.toRegisteredDeviceSafe()
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val ex = firestoreError ?: (error as? Exception)
                        if (ex != null) handleFirestoreError(ex, OperationType.READ, path)
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.READ, path)
        }
    }

    fun observeAllRegisteredDevices(): Flow<List<RegisteredDevice>> = flow {
        val user = auth.currentUser
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
            emit(emptyList())
            return@flow
        }
        val path = "registered_devices"
        try {
            // Observe the entire collection without orderBy so older/offline records missing an indexable field never drop out
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        val parsed = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toRegisteredDeviceSafe()
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipping malformed device ${doc.id}: ${e.message}")
                                null
                            }
                        }
                        deduplicateDevices(parsed)
                    }
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val ex = firestoreError ?: (error as? Exception)
                        if (ex != null && AdminAuthManager.isAuthorizedAdminGoogleUser(auth.currentUser)) {
                            handleFirestoreError(ex, OperationType.LIST, path)
                        }
                        emit(emptyList())
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emit(emptyList())
        }
    }

    suspend fun fetchAllRegisteredDevicesFromServer(): Result<List<RegisteredDevice>> {
        val path = "registered_devices"
        return try {
            requireAdminUserId()
            val snapshot = try {
                db.collection(path).get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection(path).get().await()
            }
            val parsed = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toRegisteredDeviceSafe()
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed device ${doc.id}: ${e.message}")
                    null
                }
            }
            Result.success(deduplicateDevices(parsed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Safely reconciles and removes duplicate Firestore records belonging to the same physical
     * installation (matched strictly via identical non-blank deviceFingerprint or explicit
     * legacyInstallationId link). Never merges separate physical phones of the same model.
     */
    suspend fun reconcileDuplicateDevicesIfAdmin(): Result<Int> {
        val path = "registered_devices"
        return try {
            requireAdminUserId()
            val snapshot = db.collection(path).get().await()
            val byId = linkedMapOf<String, Pair<DocumentSnapshot, RegisteredDevice>>()
            for (doc in snapshot.documents) {
                val parsed = runCatching { doc.toRegisteredDeviceSafe() }.getOrNull() ?: continue
                if (parsed.isGenuineDeviceRecord()) {
                    byId[parsed.installationId] = doc to parsed
                }
            }
            val groups = buildPhysicalDuplicateGroups(byId.values.map { it.second })
            var removedDuplicates = 0
            for (group in groups) {
                if (group.size <= 1) continue
                val merged = mergePhysicalDeviceGroup(group)
                val canonicalDoc = byId[merged.installationId]?.first ?: continue
                runCatching {
                    val earliestFirstTs = group
                        .mapNotNull { idDoc ->
                            byId[idDoc.installationId]?.first?.let { snap ->
                                snap.get("firstUsedAt") ?: snap.get("registeredAt")
                            }
                        }
                        .minByOrNull { tsValue ->
                            StoreProRepository.parseEpochMillis(tsValue, Long.MAX_VALUE)
                        }
                    val updateMap = mutableMapOf<String, Any?>(
                        "deviceName" to merged.deviceName,
                        "deviceModel" to merged.deviceModel,
                        "manufacturer" to merged.manufacturer,
                        "brand" to merged.brand,
                        "modelCode" to merged.modelCode,
                        "productName" to merged.productName,
                        "cpuArch" to merged.cpuArch,
                        "ramInfo" to merged.ramInfo,
                        "totalRam" to merged.totalRam,
                        "availableRam" to merged.availableRam,
                        "screenResolution" to merged.screenResolution,
                        "screenDensity" to merged.screenDensity,
                        "connectionType" to merged.connectionType,
                        "androidVersion" to merged.androidVersion,
                        "sdkVersion" to merged.sdkVersion,
                        "deviceType" to merged.deviceType,
                        "accountStatus" to merged.accountStatus,
                        "associatedUserId" to merged.associatedUserId,
                        "associatedEmail" to merged.associatedEmail,
                        "status" to merged.status,
                        "isAdminDevice" to merged.isAdminDevice,
                        "appVersionCode" to merged.appVersionCode,
                        "appVersionName" to merged.appVersionName,
                        "installedAt" to merged.installedAt,
                        "deviceFingerprint" to merged.deviceFingerprint
                    )
                    if (earliestFirstTs != null) {
                        updateMap["firstUsedAt"] = earliestFirstTs
                    }
                    canonicalDoc.reference.update(updateMap).await()

                    for (dup in group) {
                        if (dup.installationId != merged.installationId) {
                            byId[dup.installationId]?.first?.reference?.delete()?.await()
                            removedDuplicates++
                        }
                    }
                }
            }
            Result.success(removedDuplicates)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal error reconciling duplicate devices: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun purgeFakeEmulatorRecordsIfAdmin(): Result<Int> {
        val path = "registered_devices"
        return try {
            requireAdminUserId()
            val snapshot = db.collection(path).get().await()
            var removedCount = 0
            for (doc in snapshot.documents) {
                val parsed = runCatching { doc.toRegisteredDeviceSafe() }.getOrNull() ?: continue
                if (!parsed.isGenuineDeviceRecord()) {
                    runCatching {
                        doc.reference.delete().await()
                        removedCount++
                    }
                }
            }
            Result.success(removedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal error purging fake device entries: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun cleanupStaleDevices(
        staleThresholdMs: Long = RegisteredDevice.DEFAULT_STALE_OFFLINE_WINDOW_MS
    ): Result<Int> {
        val path = "registered_devices"
        return try {
            requireAdminUserId()
            val snapshot = db.collection(path).get().await()
            val now = System.currentTimeMillis()
            var removedCount = 0
            for (doc in snapshot.documents) {
                val parsed = runCatching { doc.toRegisteredDeviceSafe() }.getOrNull() ?: continue
                if (!parsed.isGenuineDeviceRecord() || parsed.isStaleOffline(now, staleThresholdMs)) {
                    runCatching {
                        doc.reference.delete().await()
                        removedCount++
                    }
                }
            }
            Result.success(removedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up stale devices: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun setDeviceBlocked(installationId: String, blocked: Boolean): Result<Unit> {
        val docRef = db.collection("registered_devices").document(installationId)
        return try {
            requireAdminUserId()
            require(installationId.isNotBlank()) { "Installation ID cannot be blank" }
            val nextStatus = if (blocked) RegisteredDevice.STATUS_BLOCKED else RegisteredDevice.STATUS_ACTIVE
            docRef.update("status", nextStatus).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRegisteredDevice(installationId: String): Result<Unit> {
        val docRef = db.collection("registered_devices").document(installationId)
        return try {
            requireAdminUserId()
            require(installationId.isNotBlank()) { "Installation ID cannot be blank" }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------
    // 4. FCM Token Registration & Notification Deep-Link Lookup
    // ------------------------------------------------------------

    suspend fun upsertFcmToken(
        installationId: String,
        token: String,
        notificationsAllowed: Boolean,
        appVersionCode: Long
    ): Result<Unit> {
        if (installationId.isBlank() || token.length < 10) {
            return Result.failure(IllegalArgumentException("Invalid installation ID or FCM token"))
        }
        val docRef = db.collection("fcm_tokens").document(installationId)
        return try {
            val payload = mapOf(
                "installationId" to installationId,
                "token" to token.take(1024),
                "notificationsAllowed" to notificationsAllowed,
                "appVersionCode" to appVersionCode.coerceAtLeast(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getApplicationById(appId: String): Result<ApplicationItem?> {
        if (appId.isBlank()) return Result.success(null)
        val docRef = db.collection("applications").document(appId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()
                val rawScreenshots = data["screenshots"] as? List<*>
                val screenshots = rawScreenshots?.mapNotNull { it as? String } ?: emptyList()
                Result.success(
                    ApplicationItem(
                        appId = (data["appId"] as? String)?.takeIf { it.isNotBlank() } ?: snap.id,
                        appName = data["appName"] as? String ?: "",
                        developerName = data["developerName"] as? String ?: "",
                        version = data["version"] as? String ?: "1.0.0",
                        description = data["description"] as? String ?: "",
                        iconUrl = data["iconUrl"] as? String ?: "",
                        apkUrl = data["apkUrl"] as? String ?: "",
                        screenshots = screenshots,
                        whatsNew = data["whatsNew"] as? String ?: "",
                        privacyPolicyUrl = data["privacyPolicyUrl"] as? String ?: "",
                        packageId = data["packageId"] as? String ?: "",
                        fileSizeBytes = (data["fileSizeBytes"] as? Number)?.toLong() ?: (25 * 1024 * 1024L),
                        downloads = (data["downloads"] as? Number)?.toLong() ?: 0L,
                        averageRating = (data["averageRating"] as? Number)?.toDouble() ?: 5.0,
                        ratingCount = (data["ratingCount"] as? Number)?.toLong() ?: 1L,
                        status = data["status"] as? String ?: "published",
                        verificationStatus = data["verificationStatus"] as? String ?: "verified",
                        uploadedBy = data["uploadedBy"] as? String ?: "",
                        createdAt = StoreProRepository.parseEpochMillis(data["createdAt"])
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.READ, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DeviceAndUpdateRepo"

        private fun sanitizeOptionalField(raw: String, maxLen: Int): String {
            return raw.trim()
                .takeIf { it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, true) }
                ?.take(maxLen)
                ?: ""
        }

        /**
         * Groups duplicate RegisteredDevice records ONLY when they provably belong to the same
         * physical installation (identical non-blank deviceFingerprint OR explicit legacyInstallationId
         * migration link). Separate physical phones of the exact same model are NEVER merged.
         */
        internal fun deduplicateDevices(rawList: List<RegisteredDevice>): List<RegisteredDevice> {
            val genuineById = rawList
                .filter { it.isGenuineDeviceRecord() }
                .associateBy { it.installationId }
                .values
                .toList()
            if (genuineById.isEmpty()) return emptyList()
            return buildPhysicalDuplicateGroups(genuineById)
                .map { mergePhysicalDeviceGroup(it) }
                .sortedByDescending { maxOf(it.lastActiveAt, it.registeredAt) }
        }

        internal fun buildPhysicalDuplicateGroups(devices: List<RegisteredDevice>): List<List<RegisteredDevice>> {
            if (devices.isEmpty()) return emptyList()
            val indexById = devices.mapIndexed { idx, d -> d.installationId to idx }.toMap()
            val parent = IntArray(devices.size) { it }

            fun find(x: Int): Int {
                var r = x
                while (parent[r] != r) r = parent[r]
                var cur = x
                while (cur != r) {
                    val next = parent[cur]
                    parent[cur] = r
                    cur = next
                }
                return r
            }

            fun union(a: Int, b: Int) {
                val ra = find(a)
                val rb = find(b)
                if (ra != rb) parent[rb] = ra
            }

            // 1. Link records sharing identical non-blank cryptographic physical deviceFingerprint
            val firstIdxByFingerprint = hashMapOf<String, Int>()
            devices.forEachIndexed { idx, dev ->
                val fp = dev.deviceFingerprint.trim()
                if (fp.isNotBlank()) {
                    val prev = firstIdxByFingerprint.putIfAbsent(fp, idx)
                    if (prev != null) {
                        union(prev, idx)
                    }
                }
            }

            // 2. Link explicit legacyInstallationId migration edges (same app prefs upgrade)
            devices.forEachIndexed { idx, dev ->
                val legacyId = dev.legacyInstallationId?.trim().orEmpty()
                if (legacyId.isNotBlank() && legacyId != dev.installationId) {
                    val legacyIdx = indexById[legacyId]
                    if (legacyIdx != null) {
                        union(idx, legacyIdx)
                    }
                }
            }

            return devices.indices
                .groupBy { find(it) }
                .values
                .map { indices -> indices.map { devices[it] } }
        }

        internal fun mergePhysicalDeviceGroup(group: List<RegisteredDevice>): RegisteredDevice {
            if (group.size == 1) return group.first()
            val canonical = group
                .sortedWith(
                    compareByDescending<RegisteredDevice> { it.deviceFingerprint.isNotBlank() }
                        .thenByDescending { it.legacyInstallationId != null }
                        .thenByDescending { maxOf(it.lastActiveAt, it.lastSyncAt, it.registeredAt) }
                )
                .first()
            val latestByActivity = group.maxByOrNull { maxOf(it.lastActiveAt, it.lastSyncAt, it.registeredAt) } ?: canonical
            val earliestRegisteredAt = group.mapNotNull { it.registeredAt.takeIf { ts -> ts > 0L } }.minOrNull() ?: 0L
            val earliestFirstUsedAt = group.mapNotNull { it.effectiveFirstUsedAt.takeIf { ts -> ts > 0L } }.minOrNull() ?: earliestRegisteredAt
            val latestInstalledAt = group.mapNotNull { it.installedAt.takeIf { ts -> ts > 0L } }.maxOrNull() ?: latestByActivity.installedAt
            val latestActiveAt = group.maxOfOrNull { it.lastActiveAt } ?: 0L
            val latestSyncAt = group.maxOfOrNull { it.lastSyncAt } ?: 0L
            val anyBlocked = group.any { it.isBlocked }
            val anyAdmin = group.any { it.isAdminDevice }
            val latestFingerprint = group.firstNotNullOfOrNull { it.deviceFingerprint.takeIf { fp -> fp.isNotBlank() } }.orEmpty()

            return latestByActivity.copy(
                installationId = canonical.installationId,
                deviceFingerprint = latestFingerprint,
                legacyInstallationId = canonical.legacyInstallationId,
                status = if (anyBlocked) RegisteredDevice.STATUS_BLOCKED else RegisteredDevice.STATUS_ACTIVE,
                isAdminDevice = anyAdmin,
                registeredAt = earliestRegisteredAt,
                firstUsedAt = earliestFirstUsedAt,
                installedAt = latestInstalledAt,
                lastActiveAt = latestActiveAt,
                lastSyncAt = latestSyncAt
            )
        }

        internal fun DocumentSnapshot.toRegisteredDeviceSafe(): RegisteredDevice {
            val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()
            val rawStatus = (data["status"] as? String)?.trim()?.uppercase()
            val normalizedStatus = if (rawStatus == RegisteredDevice.STATUS_BLOCKED) {
                RegisteredDevice.STATUS_BLOCKED
            } else {
                RegisteredDevice.STATUS_ACTIVE
            }
            val model = (data["deviceModel"] as? String)?.trim()?.ifBlank { "" } ?: ""
            val manufacturer = (data["manufacturer"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val brand = (data["brand"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val androidVersion = (data["androidVersion"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val sdkVersion = (data["sdkVersion"] as? Number)?.toInt() ?: 0
            val deviceType = (data["deviceType"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val associatedUid = (data["associatedUserId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val associatedEmail = (data["associatedEmail"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val rawAccountStatus = (data["accountStatus"] as? String)?.trim()?.uppercase()
            val normalizedAccountStatus = when {
                rawAccountStatus == RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN -> RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
                rawAccountStatus == RegisteredDevice.ACCOUNT_STATUS_GUEST -> RegisteredDevice.ACCOUNT_STATUS_GUEST
                !associatedUid.isNullOrBlank() -> RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
                else -> RegisteredDevice.ACCOUNT_STATUS_GUEST
            }
            val legacyRam = (data["ramInfo"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val totalRam = (data["totalRam"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: legacyRam
            val availableRam = (data["availableRam"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: ""
            val installedAtMs = StoreProRepository.parseEpochMillis(data["installedAt"], 0L)
            val registeredAtMs = StoreProRepository.parseEpochMillis(data["registeredAt"], 0L)
            val firstUsedAtMs = StoreProRepository.parseEpochMillis(data["firstUsedAt"], 0L)
            val lastActiveAtMs = StoreProRepository.parseEpochMillis(data["lastActiveAt"], 0L)
            val lastSyncAtMs = StoreProRepository.parseEpochMillis(data["lastSyncAt"], lastActiveAtMs)
            return RegisteredDevice(
                installationId = (data["installationId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                deviceName = (data["deviceName"] as? String)?.trim()?.ifBlank { "" } ?: "",
                deviceModel = model,
                manufacturer = manufacturer,
                brand = brand,
                modelCode = (data["modelCode"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: model,
                productName = (data["productName"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "",
                cpuArch = (data["cpuArch"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "",
                ramInfo = legacyRam.ifBlank { totalRam },
                totalRam = totalRam,
                availableRam = availableRam,
                screenResolution = (data["screenResolution"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "",
                screenDensity = (data["screenDensity"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "",
                connectionType = (data["connectionType"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "",
                androidVersion = androidVersion,
                sdkVersion = sdkVersion,
                deviceType = deviceType,
                accountStatus = normalizedAccountStatus,
                associatedUserId = associatedUid,
                associatedEmail = associatedEmail,
                status = normalizedStatus,
                isAdminDevice = data["isAdminDevice"] as? Boolean ?: false,
                appVersionCode = (data["appVersionCode"] as? Number)?.toLong() ?: 1L,
                appVersionName = (data["appVersionName"] as? String) ?: "1.0",
                installedAt = installedAtMs,
                registeredAt = registeredAtMs,
                firstUsedAt = if (firstUsedAtMs > 0L) firstUsedAtMs else registeredAtMs,
                lastActiveAt = lastActiveAtMs,
                lastSyncAt = lastSyncAtMs,
                deviceFingerprint = (data["deviceFingerprint"] as? String)?.trim().orEmpty(),
                legacyInstallationId = (data["legacyInstallationId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            )
        }

    }
}
