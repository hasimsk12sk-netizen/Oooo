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
        versionName: String
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
        val safeManufacturer = manufacturer.trim()
            .takeIf { it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, true) }
            ?.take(80)
            ?: ""
        val safeBrand = brand.trim()
            .takeIf { it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, true) }
            ?.take(80)
            ?: ""
        val safeAndroidVersion = androidVersion.trim()
            .takeIf { it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, true) }
            ?.take(40)
            ?: ""
        val safeSdkVersion = sdkVersion.coerceIn(0, 200)
        val safeDeviceType = deviceType.trim()
            .takeIf { it.isNotBlank() && !it.equals(RegisteredDevice.NOT_AVAILABLE, true) }
            ?.take(40)
            ?: ""
        val safeVersionCode = versionCode.coerceAtLeast(1L)
        val safeVersionName = versionName.trim().ifBlank { "1.0" }.take(40)
        val currentUser = auth.currentUser
        val accountStatus = if (currentUser != null) {
            RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
        } else {
            RegisteredDevice.ACCOUNT_STATUS_GUEST
        }
        val associatedUid = currentUser?.uid?.take(128)

        return try {
            val existingSnap = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get().await()
            }

            if (!existingSnap.exists()) {
                val createPayload = mutableMapOf<String, Any?>(
                    "installationId" to installationId,
                    "deviceName" to safeName,
                    "deviceModel" to safeModel,
                    "manufacturer" to safeManufacturer,
                    "brand" to safeBrand,
                    "androidVersion" to safeAndroidVersion,
                    "sdkVersion" to safeSdkVersion,
                    "deviceType" to safeDeviceType,
                    "accountStatus" to accountStatus,
                    "associatedUserId" to associatedUid,
                    "status" to RegisteredDevice.STATUS_ACTIVE,
                    "appVersionCode" to safeVersionCode,
                    "appVersionName" to safeVersionName,
                    "registeredAt" to FieldValue.serverTimestamp(),
                    "firstUsedAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
                val now = System.currentTimeMillis()
                val created = RegisteredDevice(
                    installationId = installationId,
                    deviceName = safeName,
                    deviceModel = safeModel,
                    manufacturer = safeManufacturer,
                    brand = safeBrand,
                    androidVersion = safeAndroidVersion,
                    sdkVersion = safeSdkVersion,
                    deviceType = safeDeviceType,
                    accountStatus = accountStatus,
                    associatedUserId = associatedUid,
                    status = RegisteredDevice.STATUS_ACTIVE,
                    appVersionCode = safeVersionCode,
                    appVersionName = safeVersionName,
                    registeredAt = now,
                    firstUsedAt = now,
                    lastActiveAt = now
                )
                Result.success(created)
            } else {
                val current = existingSnap.toRegisteredDeviceSafe()
                if (current.isBlocked) {
                    // Blocked devices cannot overwrite status or write active heartbeats
                    Result.success(current)
                } else {
                    docRef.update(
                        mapOf(
                            "deviceName" to safeName,
                            "deviceModel" to safeModel,
                            "manufacturer" to safeManufacturer,
                            "brand" to safeBrand,
                            "androidVersion" to safeAndroidVersion,
                            "sdkVersion" to safeSdkVersion,
                            "deviceType" to safeDeviceType,
                            "accountStatus" to accountStatus,
                            "associatedUserId" to associatedUid,
                            "appVersionCode" to safeVersionCode,
                            "appVersionName" to safeVersionName,
                            "lastActiveAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    Result.success(
                        current.copy(
                            deviceName = safeName,
                            deviceModel = safeModel,
                            manufacturer = safeManufacturer,
                            brand = safeBrand,
                            androidVersion = safeAndroidVersion,
                            sdkVersion = safeSdkVersion,
                            deviceType = safeDeviceType,
                            accountStatus = accountStatus,
                            associatedUserId = associatedUid,
                            appVersionCode = safeVersionCode,
                            appVersionName = safeVersionName,
                            lastActiveAt = System.currentTimeMillis()
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
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toRegisteredDeviceSafe()
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipping malformed device ${doc.id}: ${e.message}")
                                null
                            }
                        }
                            .filter { it.isGenuineDeviceRecord() }
                            .distinctBy { it.installationId }
                            .sortedByDescending { maxOf(it.lastActiveAt, it.registeredAt) }
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
            val list = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toRegisteredDeviceSafe()
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed device ${doc.id}: ${e.message}")
                    null
                }
            }
                .filter { it.isGenuineDeviceRecord() }
                .distinctBy { it.installationId }
                .sortedByDescending { maxOf(it.lastActiveAt, it.registeredAt) }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
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
            val rawAccountStatus = (data["accountStatus"] as? String)?.trim()?.uppercase()
            val normalizedAccountStatus = when {
                rawAccountStatus == RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN -> RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
                rawAccountStatus == RegisteredDevice.ACCOUNT_STATUS_GUEST -> RegisteredDevice.ACCOUNT_STATUS_GUEST
                !associatedUid.isNullOrBlank() -> RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
                else -> RegisteredDevice.ACCOUNT_STATUS_GUEST
            }
            val registeredAtMs = StoreProRepository.parseEpochMillis(data["registeredAt"], 0L)
            val firstUsedAtMs = StoreProRepository.parseEpochMillis(data["firstUsedAt"], 0L)
            return RegisteredDevice(
                installationId = (data["installationId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                deviceName = (data["deviceName"] as? String)?.trim()?.ifBlank { "" } ?: "",
                deviceModel = model,
                manufacturer = manufacturer,
                brand = brand,
                androidVersion = androidVersion,
                sdkVersion = sdkVersion,
                deviceType = deviceType,
                accountStatus = normalizedAccountStatus,
                associatedUserId = associatedUid,
                status = normalizedStatus,
                isAdminDevice = data["isAdminDevice"] as? Boolean ?: false,
                appVersionCode = (data["appVersionCode"] as? Number)?.toLong() ?: 1L,
                appVersionName = (data["appVersionName"] as? String) ?: "1.0",
                registeredAt = registeredAtMs,
                firstUsedAt = if (firstUsedAtMs > 0L) firstUsedAtMs else registeredAtMs,
                lastActiveAt = StoreProRepository.parseEpochMillis(data["lastActiveAt"], 0L)
            )
        }

    }
}
