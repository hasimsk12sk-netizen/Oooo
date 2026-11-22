package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateRelease
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
import java.io.File

data class ApkUploadResult(
    val downloadUrl: String,
    val storagePath: String,
    val fileSizeBytes: Long
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

    companion object {
        const val COLLECTION_LIVE_UPDATES = "live_updates"

        fun releaseIdForVersion(versionCode: Long): String = "release_v$versionCode"
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
     * Publicly readable stream filtered by published == true to satisfy zero-trust Firestore rules.
     */
    fun observePublishedReleases(): Flow<List<LiveUpdateRelease>> {
        val query = db.collection(COLLECTION_LIVE_UPDATES)
            .whereEqualTo("published", true)
        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { docToRelease(it) }
                    .filter { it.published && it.releaseStatus == LiveReleaseStatus.PUBLISHED.name }
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
     * Admin-only live stream exposing Published, Draft, and Archived releases.
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

    suspend fun fetchPublishedReleases(): Result<List<LiveUpdateRelease>> = withContext(Dispatchers.IO) {
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

    suspend fun fetchLatestPublishedRelease(): Result<LiveUpdateRelease?> {
        return fetchPublishedReleases().map { list -> list.firstOrNull() }
    }

    suspend fun uploadApkFile(
        localFile: File,
        versionCode: Long,
        published: Boolean,
        onProgress: (Int) -> Unit
    ): Result<ApkUploadResult> = withContext(Dispatchers.IO) {
        try {
            requireAdminUserId()
            if (!localFile.exists() || localFile.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("APK file does not exist or is empty."))
            }

            val storagePath = "releases/live_v$versionCode/storepro_v$versionCode.apk"
            val ref = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("application/vnd.android.package-archive")
                .setCustomMetadata("published", published.toString())
                .setCustomMetadata("versionCode", versionCode.toString())
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
                    fileSizeBytes = localFile.length()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveOrPublishRelease(release: LiveUpdateRelease): Result<LiveUpdateRelease> =
        withContext(Dispatchers.IO) {
            val adminUid = try {
                requireAdminUserId()
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            val releaseId = release.releaseId.ifBlank { releaseIdForVersion(release.versionCode) }
            val isPublished = release.releaseStatus == LiveReleaseStatus.PUBLISHED.name
            val docRef = db.collection(COLLECTION_LIVE_UPDATES).document(releaseId)

            val payload = hashMapOf<String, Any>(
                "releaseId" to releaseId,
                "versionName" to release.versionName.trim(),
                "versionCode" to release.versionCode,
                "packageName" to release.packageName.trim(),
                "apkDownloadUrl" to release.apkDownloadUrl.trim(),
                "apkSize" to release.apkSize,
                "releaseNotes" to release.releaseNotes.trim(),
                "releaseDate" to release.releaseDate.trim(),
                "published" to isPublished,
                "releaseStatus" to release.releaseStatus,
                "storagePath" to release.storagePath.trim(),
                "apkSha256" to release.apkSha256.trim().lowercase(),
                "signingSha256" to release.signingSha256.trim().lowercase(),
                "publishedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            try {
                // Keep Storage metadata aligned with publish visibility if binary is hosted on Storage
                if (release.storagePath.isNotBlank()) {
                    runCatching {
                        val updatedMeta = StorageMetadata.Builder()
                            .setCustomMetadata("published", isPublished.toString())
                            .build()
                        storage.reference.child(release.storagePath).updateMetadata(updatedMeta).await()
                    }
                }

                docRef.set(payload).await()
                Result.success(
                    release.copy(
                        releaseId = releaseId,
                        published = isPublished,
                        publishedBy = adminUid,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
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
                    IllegalStateException("Archive a published release before removing it.")
                )
            }

            val docRef = db.collection(COLLECTION_LIVE_UPDATES).document(release.releaseId)
            try {
                docRef.delete().await()
                if (release.storagePath.isNotBlank()) {
                    runCatching { storage.reference.child(release.storagePath).delete().await() }
                }
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
            releaseDate = doc.getString("releaseDate") ?: "",
            published = published,
            releaseStatus = status,
            storagePath = doc.getString("storagePath") ?: "",
            apkSha256 = doc.getString("apkSha256") ?: "",
            signingSha256 = doc.getString("signingSha256") ?: "",
            publishedBy = doc.getString("publishedBy") ?: "",
            updatedAt = updatedAtMillis
        )
    }
}
