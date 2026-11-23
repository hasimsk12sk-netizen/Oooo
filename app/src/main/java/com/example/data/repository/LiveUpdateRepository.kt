package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateRelease
import com.example.data.model.UpdateHistoryRecord
import com.example.ui.admin.AdminAuthManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ApkUploadResult(
    val downloadUrl: String,
    val storagePath: String,
    val fileSizeBytes: Long,
    val fileName: String
)

class LiveUpdateRepository(
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
    private val storage by lazy { Firebase.storage }
    private val availabilityClient: OkHttpClient
        get() = com.example.util.StoreProNetworkClient.baseClient

    companion object {
        const val COLLECTION_LIVE_UPDATES = "live_updates"
        const val COLLECTION_UPDATE_HISTORY = "store_pro_update_history"

        fun releaseIdForVersion(versionCode: Long): String = "release_v$versionCode"

        fun historyIdForRelease(versionCode: Long, timestampMs: Long = System.currentTimeMillis()): String =
            "hist_v${versionCode}_$timestampMs"

        fun permanentStoragePath(
            versionName: String,
            versionCode: Long,
            rawFileName: String
        ): String {
            val safeVer = versionName.trim().ifBlank { "1.0" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val baseName = rawFileName.trim()
                .ifBlank { "STORE_PRO_v${safeVer}_build${versionCode}.apk" }
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val finalFileName = if (baseName.endsWith(".apk", ignoreCase = true)) baseName else "$baseName.apk"
            return "STORE_PRO/RELEASES/v${safeVer}_b${versionCode}/$finalFileName"
        }
    }

    private fun requireAdminUserId(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Administrator Google Sign-In is required.")
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
            throw SecurityException("Access Denied: Verified administrator authorization required.")
        }
        return user.uid
    }

    /**
     * Admin-only live stream exposing all releases and drafts.
     */
    fun observeAllReleasesForAdmin(): Flow<List<LiveUpdateRelease>> {
        val currentUser = auth.currentUser
        if (currentUser == null || !AdminAuthManager.isAuthorizedAdminGoogleUser(currentUser)) {
            return flow { emit(emptyList()) }
        }
        return db.collection(COLLECTION_LIVE_UPDATES)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { docToRelease(it) }
                    .sortedByDescending { it.versionCode }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e as? Exception ?: Exception(e), OperationType.LIST, COLLECTION_LIVE_UPDATES)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Admin-only live stream of immutable historical release entries.
     */
    fun observeUpdateHistoryForAdmin(): Flow<List<UpdateHistoryRecord>> {
        val currentUser = auth.currentUser
        if (currentUser == null || !AdminAuthManager.isAuthorizedAdminGoogleUser(currentUser)) {
            return flow { emit(emptyList()) }
        }
        return db.collection(COLLECTION_UPDATE_HISTORY)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { docToHistory(it) }
                    .sortedWith(compareByDescending<UpdateHistoryRecord> { it.releaseTimestamp }.thenByDescending { it.versionCode })
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e as? Exception ?: Exception(e), OperationType.LIST, COLLECTION_UPDATE_HISTORY)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun fetchUpdateHistoryForAdmin(): Result<List<UpdateHistoryRecord>> = withContext(Dispatchers.IO) {
        try {
            requireAdminUserId()
            val snapshot = try {
                db.collection(COLLECTION_UPDATE_HISTORY).get(Source.SERVER).await()
            } catch (_: Exception) {
                db.collection(COLLECTION_UPDATE_HISTORY).get().await()
            }
            val list = snapshot.documents.mapNotNull { docToHistory(it) }
                .sortedWith(compareByDescending<UpdateHistoryRecord> { it.releaseTimestamp }.thenByDescending { it.versionCode })
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COLLECTION_UPDATE_HISTORY)
            Result.failure(e)
        }
    }

    suspend fun fetchPublishedReleases(): Result<List<LiveUpdateRelease>> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null || !AdminAuthManager.isAuthorizedAdminGoogleUser(currentUser)) {
            return@withContext Result.success(emptyList())
        }
        val query = db.collection(COLLECTION_LIVE_UPDATES)
            .whereEqualTo("published", true)
        try {
            val snapshot = try {
                query.get(Source.SERVER).await()
            } catch (_: Exception) {
                query.get().await()
            }
            val list = snapshot.documents.mapNotNull { docToRelease(it) }
                .filter { it.published && it.releaseStatus == LiveReleaseStatus.PUBLISHED.name }
                .sortedByDescending { it.versionCode }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COLLECTION_LIVE_UPDATES)
            Result.failure(e)
        }
    }

    suspend fun uploadApkFile(
        localFile: File,
        versionName: String,
        versionCode: Long,
        originalFileName: String = localFile.name,
        published: Boolean,
        onProgress: (Int) -> Unit
    ): Result<ApkUploadResult> = withContext(Dispatchers.IO) {
        try {
            requireAdminUserId()
            if (!localFile.exists() || localFile.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("APK file does not exist or is empty."))
            }

            // Use unique permanent path per release; append timestamp suffix if an identically named binary already exists
            var storagePath = permanentStoragePath(versionName, versionCode, originalFileName)
            val existingCheck = runCatching { storage.reference.child(storagePath).metadata.await() }.getOrNull()
            if (existingCheck != null) {
                val safeVer = versionName.trim().ifBlank { "1.0" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                storagePath = "STORE_PRO/RELEASES/v${safeVer}_b${versionCode}/${System.currentTimeMillis()}_${originalFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            }

            val ref = storage.reference.child(storagePath)
            val resolvedFileName = storagePath.substringAfterLast('/')
            val metadata = StorageMetadata.Builder()
                .setContentType("application/vnd.android.package-archive")
                .setCustomMetadata("published", published.toString())
                .setCustomMetadata("versionCode", versionCode.toString())
                .setCustomMetadata("versionName", versionName)
                .build()

            onProgress(1)
            val uploadTask = ref.putFile(Uri.fromFile(localFile), metadata)
            uploadTask.addOnProgressListener { taskSnapshot ->
                val total = taskSnapshot.totalByteCount
                if (total > 0L) {
                    val pct = ((taskSnapshot.bytesTransferred * 100L) / total).toInt().coerceIn(1, 99)
                    onProgress(pct)
                }
            }
            uploadTask.await()
            val downloadUrl = ref.downloadUrl.await().toString()
            onProgress(100)

            Result.success(
                ApkUploadResult(
                    downloadUrl = downloadUrl,
                    storagePath = storagePath,
                    fileSizeBytes = localFile.length(),
                    fileName = resolvedFileName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveOrPublishRelease(
        release: LiveUpdateRelease,
        previousVersionLabel: String = ""
    ): Result<LiveUpdateRelease> = withContext(Dispatchers.IO) {
        val adminUid = try {
            requireAdminUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val userEmail = auth.currentUser?.email ?: AdminAuthManager.AUTHORIZED_ADMIN_EMAIL
        val releaseId = release.releaseId.ifBlank { releaseIdForVersion(release.versionCode) }
        val isPublished = release.releaseStatus == LiveReleaseStatus.PUBLISHED.name
        val nowMs = System.currentTimeMillis()
        val historyId = release.historyId.ifBlank { historyIdForRelease(release.versionCode, nowMs) }
        val resolvedApkName = release.apkFileName.ifBlank {
            release.storagePath.substringAfterLast('/').ifBlank {
                "STORE_PRO_v${release.versionName}_build${release.versionCode}.apk"
            }
        }
        val docRef = db.collection(COLLECTION_LIVE_UPDATES).document(releaseId)

        val payload = hashMapOf<String, Any>(
            "releaseId" to releaseId,
            "versionName" to release.versionName.trim(),
            "versionCode" to release.versionCode,
            "buildNumber" to release.versionCode,
            "packageName" to release.packageName.trim(),
            "apkDownloadUrl" to release.apkDownloadUrl.trim(),
            "apkSize" to release.apkSize,
            "apkFileName" to resolvedApkName,
            "releaseNotes" to release.releaseNotes.trim(),
            "changelog" to release.changelog.ifBlank { release.releaseNotes }.trim(),
            "releaseDate" to release.releaseDate.trim(),
            "mandatoryUpdate" to release.mandatoryUpdate,
            "published" to isPublished,
            "releaseStatus" to release.releaseStatus,
            "storagePath" to release.storagePath.trim(),
            "apkSha256" to release.apkSha256.trim().lowercase(),
            "signingSha256" to release.signingSha256.trim().lowercase(),
            "adminEmail" to userEmail.take(200),
            "historyId" to historyId,
            "publishedBy" to adminUid,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()

            // Always create an immutable historical record whenever a release is published (or saved if it doesn't have one)
            if (isPublished) {
                val timeFormatted = SimpleDateFormat("HH:mm:ss z", Locale.US).format(Date(nowMs))
                val historyDoc = db.collection(COLLECTION_UPDATE_HISTORY).document(historyId)
                val historyPayload = hashMapOf<String, Any>(
                    "historyId" to historyId,
                    "versionName" to release.versionName.trim(),
                    "versionCode" to release.versionCode,
                    "buildNumber" to release.versionCode,
                    "previousVersion" to previousVersionLabel.take(60),
                    "releaseDate" to release.releaseDate.trim(),
                    "publicationTime" to timeFormatted,
                    "releaseTimestamp" to FieldValue.serverTimestamp(),
                    "releaseNotes" to release.releaseNotes.trim(),
                    "changelog" to release.changelog.ifBlank { release.releaseNotes }.trim(),
                    "mandatoryUpdate" to release.mandatoryUpdate,
                    "publicationStatus" to release.releaseStatus,
                    "apkFileName" to resolvedApkName,
                    "apkFileSize" to release.apkSize,
                    "apkDownloadUrl" to release.apkDownloadUrl.trim(),
                    "apkStoragePath" to release.storagePath.trim(),
                    "packageName" to release.packageName.trim(),
                    "signingCertificateFingerprint" to release.signingSha256.trim().lowercase(),
                    "apkAvailable" to release.apkDownloadUrl.trim().startsWith("https://"),
                    "adminUid" to adminUid,
                    "adminEmail" to userEmail.take(200),
                    "publishedBy" to adminUid
                )
                historyDoc.set(historyPayload).await()
            }

            Result.success(
                release.copy(
                    releaseId = releaseId,
                    historyId = historyId,
                    apkFileName = resolvedApkName,
                    adminEmail = userEmail,
                    published = isPublished,
                    publishedBy = adminUid,
                    updatedAt = nowMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun verifyApkUrlReachable(url: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) return@withContext false
        runCatching {
            val headReq = Request.Builder().url(cleanUrl).head().build()
            availabilityClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) return@runCatching true
            }
            val rangeReq = Request.Builder().url(cleanUrl).header("Range", "bytes=0-3").build()
            availabilityClient.newCall(rangeReq).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    suspend fun updateHistoryApkAvailability(historyId: String, available: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireAdminUserId()
                db.collection(COLLECTION_UPDATE_HISTORY).document(historyId)
                    .update("apkAvailable", available)
                    .await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun archiveRelease(release: LiveUpdateRelease): Result<LiveUpdateRelease> {
        return saveOrPublishRelease(
            release.copy(
                published = false,
                releaseStatus = LiveReleaseStatus.ARCHIVED.name
            )
        )
    }

    suspend fun deleteDraftRelease(release: LiveUpdateRelease): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                requireAdminUserId()
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            if (release.published || release.releaseStatus == LiveReleaseStatus.PUBLISHED.name) {
                return@withContext Result.failure(
                    IllegalStateException("Published releases cannot be deleted.")
                )
            }

            val docRef = db.collection(COLLECTION_LIVE_UPDATES).document(release.releaseId)
            try {
                docRef.delete().await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, docRef.path)
                Result.failure(e)
            }
        }

    private fun docToRelease(doc: DocumentSnapshot): LiveUpdateRelease? {
        if (!doc.exists()) return null
        val versionCode = doc.getLong("versionCode") ?: return null
        val releaseId = doc.getString("releaseId") ?: doc.id
        val status = doc.getString("releaseStatus") ?: LiveReleaseStatus.DRAFT.name
        val published = doc.getBoolean("published") ?: (status == LiveReleaseStatus.PUBLISHED.name)
        val updatedAtRaw = doc.get("updatedAt")
        val updatedAtMillis = when (updatedAtRaw) {
            is Timestamp -> updatedAtRaw.toDate().time
            is Number -> updatedAtRaw.toLong()
            else -> 0L
        }
        return LiveUpdateRelease(
            releaseId = releaseId,
            versionName = doc.getString("versionName") ?: "",
            versionCode = versionCode,
            packageName = doc.getString("packageName") ?: "",
            apkDownloadUrl = doc.getString("apkDownloadUrl") ?: "",
            apkSize = doc.getLong("apkSize") ?: 0L,
            releaseNotes = doc.getString("releaseNotes") ?: "",
            changelog = doc.getString("changelog") ?: (doc.getString("releaseNotes") ?: ""),
            releaseDate = doc.getString("releaseDate") ?: "",
            mandatoryUpdate = doc.getBoolean("mandatoryUpdate") ?: false,
            published = published,
            releaseStatus = status,
            storagePath = doc.getString("storagePath") ?: "",
            apkFileName = doc.getString("apkFileName") ?: "",
            apkSha256 = doc.getString("apkSha256") ?: "",
            signingSha256 = doc.getString("signingSha256") ?: "",
            adminEmail = doc.getString("adminEmail") ?: "",
            historyId = doc.getString("historyId") ?: "",
            publishedBy = doc.getString("publishedBy") ?: "",
            updatedAt = updatedAtMillis
        )
    }

    private fun docToHistory(doc: DocumentSnapshot): UpdateHistoryRecord? {
        if (!doc.exists()) return null
        val versionCode = doc.getLong("versionCode") ?: return null
        val tsRaw = doc.get("releaseTimestamp")
        val tsMillis = when (tsRaw) {
            is Timestamp -> tsRaw.toDate().time
            is Number -> tsRaw.toLong()
            else -> 0L
        }
        val apkUrl = doc.getString("apkDownloadUrl") ?: ""
        return UpdateHistoryRecord(
            historyId = doc.getString("historyId") ?: doc.id,
            versionName = doc.getString("versionName") ?: "",
            versionCode = versionCode,
            buildNumber = doc.getLong("buildNumber") ?: versionCode,
            previousVersion = doc.getString("previousVersion") ?: "",
            releaseDate = doc.getString("releaseDate") ?: "",
            publicationTime = doc.getString("publicationTime") ?: "",
            releaseTimestamp = tsMillis,
            releaseNotes = doc.getString("releaseNotes") ?: "",
            changelog = doc.getString("changelog") ?: (doc.getString("releaseNotes") ?: ""),
            mandatoryUpdate = doc.getBoolean("mandatoryUpdate") ?: false,
            publicationStatus = doc.getString("publicationStatus") ?: LiveReleaseStatus.PUBLISHED.name,
            apkFileName = doc.getString("apkFileName") ?: "",
            apkFileSize = doc.getLong("apkFileSize") ?: 0L,
            apkDownloadUrl = apkUrl,
            apkStoragePath = doc.getString("apkStoragePath") ?: "",
            packageName = doc.getString("packageName") ?: "",
            signingCertificateFingerprint = doc.getString("signingCertificateFingerprint") ?: "",
            apkAvailable = doc.getBoolean("apkAvailable") ?: apkUrl.startsWith("https://"),
            adminUid = doc.getString("adminUid") ?: (doc.getString("publishedBy") ?: ""),
            adminEmail = doc.getString("adminEmail") ?: "",
            publishedBy = doc.getString("publishedBy") ?: ""
        )
    }
}
