package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ApplicationItem
import com.example.data.model.DownloadItem
import com.example.data.model.ReviewItem
import com.example.data.model.SavedAppItem
import com.example.data.model.UserProfile
import com.example.ui.admin.AdminAuthManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class StoreProRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val downloadActionMutex = Mutex()
    private val processedDownloadActions = LinkedHashSet<String>()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before writing user-scoped data.")
    }

    private fun requireAdminUserId(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Administrator Google Sign-In is required.")
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) {
            throw SecurityException("Access Denied: Only the authorized administrator can modify marketplace applications.")
        }
        return user.uid
    }

    // 1. Applications Stream (Public read for guests and signed-in users; resilient to Timestamp or Long createdAt)
    fun observeApplications(): Flow<List<ApplicationItem>> = flow {
        val path = "applications"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toApplicationItemSafe()
                            } catch (e: Exception) {
                                Log.w("StoreProRepository", "Skipping malformed application doc ${doc.id}: ${e.message}")
                                null
                            }
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
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

    // 2. Upload / Create Application (Admin Only)
    suspend fun uploadApplication(app: ApplicationItem): Result<Unit> {
        var docPath = "applications/${app.appId}"
        return try {
            val uid = requireAdminUserId()
            val docRef = db.collection("applications").document(app.appId)
            docPath = docRef.path
            val payload = app.copy(
                appName = app.appName.trim().take(120),
                developerName = app.developerName.trim().take(120),
                version = app.version.trim().take(40),
                description = app.description.trim().take(5000),
                iconUrl = app.iconUrl.trim().take(2000),
                apkUrl = app.apkUrl.trim().take(2000),
                screenshots = app.screenshots.take(20),
                whatsNew = app.whatsNew.trim().take(2000),
                privacyPolicyUrl = app.privacyPolicyUrl.trim().take(2000),
                packageId = app.packageId.trim().take(150),
                fileSizeBytes = app.fileSizeBytes.coerceAtLeast(0L),
                uploadedBy = uid
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    // 3. Update Application (Admin Only; preserves immutable uploadedBy & createdAt)
    suspend fun updateApplication(app: ApplicationItem): Result<Unit> {
        var docPath = "applications/${app.appId}"
        return try {
            requireAdminUserId()
            val docRef = db.collection("applications").document(app.appId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "appName" to app.appName.trim().take(120),
                    "developerName" to app.developerName.trim().take(120),
                    "version" to app.version.trim().take(40),
                    "description" to app.description.trim().take(5000),
                    "iconUrl" to app.iconUrl.trim().take(2000),
                    "apkUrl" to app.apkUrl.trim().take(2000),
                    "screenshots" to app.screenshots.take(20),
                    "whatsNew" to app.whatsNew.trim().take(2000),
                    "privacyPolicyUrl" to app.privacyPolicyUrl.trim().take(2000)
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // 4. Delete Application (Admin Only)
    suspend fun deleteApplication(appId: String): Result<Unit> {
        var docPath = "applications/$appId"
        return try {
            requireAdminUserId()
            val docRef = db.collection("applications").document(appId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // 5. Unified Download Counter (Allowed for both guests and signed-in users; deduplicated per actionId)
    suspend fun incrementDownloads(appId: String, actionId: String? = null): Result<Unit> {
        if (appId.isBlank()) return Result.failure(IllegalArgumentException("App ID must not be blank"))
        if (actionId != null) {
            val alreadyCounted = downloadActionMutex.withLock {
                if (processedDownloadActions.contains(actionId)) {
                    true
                } else {
                    if (processedDownloadActions.size >= 500) {
                        val oldest = processedDownloadActions.iterator()
                        if (oldest.hasNext()) {
                            oldest.next()
                            oldest.remove()
                        }
                    }
                    processedDownloadActions.add(actionId)
                    false
                }
            }
            if (alreadyCounted) {
                return Result.success(Unit)
            }
        }
        val docRef = db.collection("applications").document(appId)
        return try {
            docRef.update("downloads", FieldValue.increment(1)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            if (actionId != null) {
                downloadActionMutex.withLock { processedDownloadActions.remove(actionId) }
            }
            throw e
        } catch (e: Exception) {
            if (actionId != null) {
                downloadActionMutex.withLock { processedDownloadActions.remove(actionId) }
            }
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // 6. User Profile
    suspend fun getUserProfileResult(userId: String): Result<UserProfile?> {
        if (userId.isBlank()) return Result.success(null)
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            Result.success(if (!snap.exists()) null else snap.toUserProfileSafe())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.READ, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        return getUserProfileResult(userId).getOrNull()
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val sanitized = profile.copy(
                userId = uid,
                email = profile.email.take(200),
                displayName = profile.displayName.takeIf { it.isNotBlank() }?.take(100) ?: "STORE PRO User",
                username = profile.username.takeIf { it.isNotBlank() }?.take(60) ?: "user_${uid.take(6)}",
                avatarUrl = profile.avatarUrl?.takeIf { it.length <= 1500 }
            )
            docRef.set(sanitized).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // 7. Saved Apps / Wishlist (Empty stream when unauthenticated)
    fun observeSavedApps(userId: String?): Flow<List<SavedAppItem>> = flow {
        if (userId.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/saved_apps"
        try {
            emitAll(
                db.collection("users").document(userId).collection("saved_apps")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toSavedAppItemSafe()
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        emit(emptyList())
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser?.uid == userId) {
                handleFirestoreError(e, OperationType.LIST, path)
            }
            emit(emptyList())
        }
    }

    suspend fun saveApp(userId: String, app: ApplicationItem): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("saved_apps").document(app.appId)
        val item = SavedAppItem(
            appId = app.appId,
            appName = app.appName.takeIf { it.isNotBlank() }?.take(120) ?: "App",
            developerName = app.developerName.take(120),
            iconUrl = app.iconUrl.take(2000),
            version = app.version.take(40)
        )
        return try {
            val uid = requireUserId()
            require(uid == userId) { "User mismatch while saving bookmark." }
            docRef.set(item).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeSavedApp(userId: String, appId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("saved_apps").document(appId)
        return try {
            val uid = requireUserId()
            require(uid == userId) { "User mismatch while removing bookmark." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // 8. Download History (Empty stream when unauthenticated)
    fun observeDownloads(userId: String?): Flow<List<DownloadItem>> = flow {
        if (userId.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/downloads"
        try {
            emitAll(
                db.collection("users").document(userId).collection("downloads")
                    .orderBy("downloadedAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toDownloadItemSafe()
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                        }
                        emit(emptyList())
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser?.uid == userId) {
                handleFirestoreError(e, OperationType.LIST, path)
            }
            emit(emptyList())
        }
    }

    suspend fun recordDownload(userId: String?, item: DownloadItem) {
        if (userId.isNullOrBlank() || auth.currentUser?.uid != userId) return
        val docId = if (item.downloadId.isNotBlank()) item.downloadId else "dl_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(userId).collection("downloads").document(docId)
        try {
            val sanitized = item.copy(
                downloadId = docId,
                appName = item.appName.takeIf { it.isNotBlank() }?.take(120) ?: "App",
                version = item.version.take(40),
                iconUrl = item.iconUrl.take(2000),
                downloadUrl = item.downloadUrl.take(2000),
                fileSize = item.fileSize.coerceAtLeast(0L),
                localFilePath = item.localFilePath.take(2500)
            )
            docRef.set(sanitized).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    // 9. Reviews (Public read; Authenticated create/update/delete by author)
    fun observeReviews(appId: String): Flow<List<ReviewItem>> = flow {
        if (appId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "reviews"
        try {
            emitAll(
                db.collection(path)
                    .whereEqualTo("appId", appId)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toReviewItemSafe()
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null) {
                            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
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

    suspend fun submitReview(
        appId: String,
        rating: Long,
        comment: String,
        userName: String,
        userAvatar: String?,
        existingReviewId: String? = null
    ): Result<Unit> {
        var docPath = "reviews"
        return try {
            val uid = requireUserId()
            val reviewId = existingReviewId ?: "rev_${uid}_$appId"
            val docRef = db.collection("reviews").document(reviewId)
            docPath = docRef.path

            val item = ReviewItem(
                reviewId = reviewId,
                appId = appId,
                userId = uid,
                userName = userName.trim().ifBlank { "STORE PRO User" }.take(100),
                userAvatar = userAvatar?.takeIf { it.length <= 1500 },
                rating = rating.coerceIn(1L, 5L),
                comment = comment.trim().take(2000),
                timestamp = System.currentTimeMillis()
            )
            docRef.set(item).await()

            val allSnaps = db.collection("reviews").whereEqualTo("appId", appId).get().await()
            val list = allSnaps.documents.mapNotNull { doc ->
                try {
                    doc.toReviewItemSafe()
                } catch (_: Exception) {
                    null
                }
            }
            val avg = if (list.isNotEmpty()) list.map { it.rating }.average() else 5.0
            db.collection("applications").document(appId).update(
                mapOf(
                    "averageRating" to avg,
                    "ratingCount" to list.size.toLong()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReview(reviewId: String, appId: String): Result<Unit> {
        val docRef = db.collection("reviews").document(reviewId)
        return try {
            requireUserId()
            docRef.delete().await()
            val allSnaps = db.collection("reviews").whereEqualTo("appId", appId).get().await()
            val list = allSnaps.documents.mapNotNull { doc ->
                try {
                    doc.toReviewItemSafe()
                } catch (_: Exception) {
                    null
                }
            }
            val avg = if (list.isNotEmpty()) list.map { it.rating }.average() else 5.0
            db.collection("applications").document(appId).update(
                mapOf(
                    "averageRating" to avg,
                    "ratingCount" to list.size.toLong()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // 10. Initial Marketplace Seeding (Admin Only)
    suspend fun seedMarketplaceIfEmpty() {
        val user = auth.currentUser ?: return
        if (!AdminAuthManager.isAuthorizedAdminGoogleUser(user)) return

        try {
            val snap = db.collection("applications").limit(1).get().await()
            if (!snap.isEmpty) return

            val uid = user.uid
            val seedApps = listOf(
                ApplicationItem(
                    appId = "seed_vlc",
                    appName = "VLC Media Player",
                    developerName = "VideoLAN",
                    version = "3.5.4",
                    description = "VLC for Android is the best open-source video and music player, fast and easy! Plays all video formats with hardware acceleration.",
                    iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=256&h=256&fit=crop",
                    apkUrl = "https://get.videolan.org/vlc-android/3.5.4/VLC-Android-3.5.4-arm64-v8a.apk",
                    screenshots = listOf(
                        "https://images.unsplash.com/photo-1536240478700-b869070f9279?w=600",
                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"
                    ),
                    whatsNew = "Performance enhancements and audio equalizer optimizations.",
                    packageId = "org.videolan.vlc",
                    fileSizeBytes = 34 * 1024 * 1024L,
                    downloads = 1420L,
                    averageRating = 4.9,
                    ratingCount = 82L,
                    uploadedBy = uid
                ),
                ApplicationItem(
                    appId = "seed_signal",
                    appName = "Signal Messenger",
                    developerName = "Signal Foundation",
                    version = "7.2.1",
                    description = "State-of-the-art end-to-end encryption keeps your conversations secure. Privacy isn't an optional mode — it's just the way Signal works.",
                    iconUrl = "https://images.unsplash.com/photo-1614680376593-902f749f7ffc?w=256&h=256&fit=crop",
                    apkUrl = "https://updates.signal.org/android/Signal-Android-website-prod-universal-release-7.2.1.apk",
                    screenshots = listOf(
                        "https://images.unsplash.com/photo-1577563908411-5077b6dc7624?w=600"
                    ),
                    whatsNew = "Group story improvements and high-definition video calling.",
                    packageId = "org.thoughtcrime.securesms",
                    fileSizeBytes = 48 * 1024 * 1024L,
                    downloads = 980L,
                    averageRating = 4.8,
                    ratingCount = 64L,
                    uploadedBy = uid
                ),
                ApplicationItem(
                    appId = "seed_kodi",
                    appName = "Kodi Entertainment Center",
                    developerName = "XBMC Foundation",
                    version = "21.0.0",
                    description = "Award-winning free and open-source home theater media hub for all your local music, movies, photos, and streaming.",
                    iconUrl = "https://images.unsplash.com/photo-1593305841991-05c297ba4575?w=256&h=256&fit=crop",
                    apkUrl = "https://mirrors.kodi.tv/releases/android/arm64-v8a/kodi-21.0-Omega-arm64-v8a.apk",
                    screenshots = listOf(
                        "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600"
                    ),
                    whatsNew = "Omega 21.0 release with FFmpeg 6.0 and improved HDR support.",
                    packageId = "org.xbmc.kodi",
                    fileSizeBytes = 68 * 1024 * 1024L,
                    downloads = 760L,
                    averageRating = 4.7,
                    ratingCount = 49L,
                    uploadedBy = uid
                )
            )

            for (app in seedApps) {
                db.collection("applications").document(app.appId).set(app).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "applications")
        }
    }

    companion object {
        internal fun parseEpochMillis(raw: Any?, fallback: Long = System.currentTimeMillis()): Long {
            return when (raw) {
                is Timestamp -> raw.toDate().time
                is java.util.Date -> raw.time
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull() ?: fallback
                else -> fallback
            }
        }

        private fun DocumentSnapshot.getDataWithEstimatedTimestamps(): Map<String, Any>? {
            return getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }

        private fun DocumentSnapshot.toApplicationItemSafe(): ApplicationItem {
            val data = getDataWithEstimatedTimestamps().orEmpty()
            val rawScreenshots = data["screenshots"] as? List<*>
            val screenshots = rawScreenshots?.mapNotNull { it as? String } ?: emptyList()

            return ApplicationItem(
                appId = (data["appId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
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
                createdAt = parseEpochMillis(data["createdAt"])
            )
        }

        private fun DocumentSnapshot.toReviewItemSafe(): ReviewItem {
            val data = getDataWithEstimatedTimestamps().orEmpty()
            return ReviewItem(
                reviewId = (data["reviewId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                appId = data["appId"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                userName = data["userName"] as? String ?: "STORE PRO User",
                userAvatar = data["userAvatar"] as? String,
                rating = (data["rating"] as? Number)?.toLong() ?: 5L,
                comment = data["comment"] as? String ?: "",
                timestamp = parseEpochMillis(data["timestamp"])
            )
        }

        private fun DocumentSnapshot.toSavedAppItemSafe(): SavedAppItem {
            val data = getDataWithEstimatedTimestamps().orEmpty()
            return SavedAppItem(
                appId = (data["appId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                appName = data["appName"] as? String ?: "",
                developerName = data["developerName"] as? String ?: "",
                iconUrl = data["iconUrl"] as? String ?: "",
                version = data["version"] as? String ?: "",
                savedAt = parseEpochMillis(data["savedAt"])
            )
        }

        private fun DocumentSnapshot.toDownloadItemSafe(): DownloadItem {
            val data = getDataWithEstimatedTimestamps().orEmpty()
            return DownloadItem(
                downloadId = (data["downloadId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                appId = data["appId"] as? String ?: "",
                appName = data["appName"] as? String ?: "",
                version = data["version"] as? String ?: "",
                iconUrl = data["iconUrl"] as? String ?: "",
                downloadUrl = data["downloadUrl"] as? String ?: "",
                fileSize = (data["fileSize"] as? Number)?.toLong() ?: 0L,
                localFilePath = data["localFilePath"] as? String ?: "",
                downloadedAt = parseEpochMillis(data["downloadedAt"])
            )
        }

        private fun DocumentSnapshot.toUserProfileSafe(): UserProfile {
            val data = getDataWithEstimatedTimestamps().orEmpty()
            return UserProfile(
                userId = (data["userId"] as? String)?.takeIf { it.isNotBlank() } ?: id,
                email = data["email"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                username = data["username"] as? String ?: "",
                avatarUrl = data["avatarUrl"] as? String
            )
        }

    }
}
