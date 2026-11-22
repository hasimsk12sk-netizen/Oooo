package com.example.data.liveupdate

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.pm.PackageInfoCompat
import com.example.data.model.InspectedApkMetadata
import com.example.data.model.InstalledAppDetails
import com.example.data.model.LiveUpdateRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile

data class ApkVerificationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val matchedSigningSha256: String = ""
)

object LiveUpdateApkVerifier {

    private const val MAX_APK_BYTES = 300L * 1024L * 1024L

    @Suppress("DEPRECATION")
    fun readInstalledAppDetails(context: Context): InstalledAppDetails {
        val pm = context.packageManager
        val pkgName = context.packageName
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(pkgName, flags)
            }
        }.getOrElse {
            pm.getPackageInfo(pkgName, 0)
        }

        val appName = pkgInfo.applicationInfo?.let { info ->
            pm.getApplicationLabel(info).toString()
        }?.takeIf { it.isNotBlank() } ?: "STORE PRO"

        val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val signingHashes = extractSigningSha256(pkgInfo)

        return InstalledAppDetails(
            appName = appName,
            packageName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            androidVersion = Build.VERSION.RELEASE ?: "Android",
            apiLevel = Build.VERSION.SDK_INT,
            signingSha256Set = signingHashes
        )
    }

    suspend fun inspectPickedUri(
        context: Context,
        uri: Uri
    ): Result<InspectedApkMetadata> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val displayName = resolveDisplayName(context, uri)
            val inspectDir = File(context.cacheDir, "live_update_inspect").apply { mkdirs() }
            inspectDir.listFiles()?.forEach { it.delete() }
            val tempApk = File(inspectDir, "candidate_release.apk")

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempApk).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read = input.read(buffer)
                    while (read != -1) {
                        totalBytes += read
                        if (totalBytes > MAX_APK_BYTES) {
                            tempApk.delete()
                            return@withContext Result.failure(
                                IllegalArgumentException("APK file exceeds the 300 MB limit.")
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(
                IllegalStateException("Unable to read the selected APK file.")
            )

            if (totalBytes <= 0L) {
                tempApk.delete()
                return@withContext Result.failure(
                    IllegalArgumentException("Selected APK file is empty (0 bytes).")
                )
            }

            val apkSha256 = bytesToHex(digest.digest())
            val inspected = inspectApkFile(
                context = context,
                file = tempApk,
                displayName = displayName,
                precomputedSha256 = apkSha256
            )
            Result.success(inspected)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    fun inspectApkFile(
        context: Context,
        file: File,
        displayName: String = file.name,
        precomputedSha256: String? = null
    ): InspectedApkMetadata {
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return InspectedApkMetadata(
                fileName = displayName,
                localTempPath = file.absolutePath,
                packageName = "",
                versionName = "",
                versionCode = 0L,
                fileSizeBytes = 0L,
                apkSha256 = "",
                signingSha256Set = emptySet(),
                isValidArchive = false,
                errorMessage = "APK file is missing or empty."
            )
        }

        val fileSize = file.length()
        val fileSha256 = precomputedSha256 ?: computeFileSha256(file)
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(file.absolutePath, flags)
            }
        }.getOrNull() ?: runCatching {
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull()

        if (pkgInfo == null) {
            return InspectedApkMetadata(
                fileName = displayName,
                localTempPath = file.absolutePath,
                packageName = "",
                versionName = "",
                versionCode = 0L,
                fileSizeBytes = fileSize,
                apkSha256 = fileSha256,
                signingSha256Set = emptySet(),
                isValidArchive = false,
                errorMessage = "Invalid or corrupted APK archive."
            )
        }

        pkgInfo.applicationInfo?.sourceDir = file.absolutePath
        pkgInfo.applicationInfo?.publicSourceDir = file.absolutePath

        val archivePkgName = pkgInfo.packageName ?: ""
        val archiveVersionName = pkgInfo.versionName ?: ""
        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val signingHashes = extractSigningSha256(pkgInfo)
            .ifEmpty { extractSigningSha256FromApkZip(file) }

        return InspectedApkMetadata(
            fileName = displayName,
            localTempPath = file.absolutePath,
            packageName = archivePkgName,
            versionName = archiveVersionName,
            versionCode = archiveVersionCode,
            fileSizeBytes = fileSize,
            apkSha256 = fileSha256,
            signingSha256Set = signingHashes,
            isValidArchive = archivePkgName.isNotBlank() && archiveVersionCode > 0L,
            errorMessage = if (archivePkgName.isBlank() || archiveVersionCode <= 0L) {
                "APK metadata is incomplete or corrupted."
            } else {
                null
            }
        )
    }

    fun verifyForAdminPublish(
        inspected: InspectedApkMetadata,
        installed: InstalledAppDetails,
        enteredVersionName: String,
        enteredVersionCode: Long,
        highestPublishedVersionCode: Long
    ): ApkVerificationResult {
        val errors = mutableListOf<String>()
        if (!inspected.isValidArchive) {
            errors += (inspected.errorMessage ?: "Selected file is not a valid APK package.")
        }
        if (inspected.fileSizeBytes <= 0L) {
            errors += "Selected APK file size must be greater than zero."
        }
        if (inspected.packageName != installed.packageName) {
            errors += "Package mismatch: APK is '${inspected.packageName}', expected '${installed.packageName}'."
        }
        if (enteredVersionCode <= 0L) {
            errors += "Version Code must be a positive integer."
        } else if (inspected.versionCode != enteredVersionCode) {
            errors += "Version Code mismatch: APK reports ${inspected.versionCode}, form has $enteredVersionCode."
        }
        val cleanVersionName = enteredVersionName.trim()
        if (cleanVersionName.isEmpty()) {
            errors += "Version Name cannot be blank."
        } else if (inspected.versionName != cleanVersionName) {
            errors += "Version Name mismatch: APK reports '${inspected.versionName}', form has '$cleanVersionName'."
        }
        if (inspected.versionCode <= highestPublishedVersionCode) {
            errors += "New Version Code (${inspected.versionCode}) must be higher than current published Version Code ($highestPublishedVersionCode)."
        }

        val matchedSigning = resolveMatchedSigningSha256(
            apkSigning = inspected.signingSha256Set,
            installedSigning = installed.signingSha256Set,
            expectedReleaseSigning = "",
            errors = errors
        )

        return ApkVerificationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            matchedSigningSha256 = matchedSigning
        )
    }

    fun verifyForUserInstall(
        context: Context,
        downloadedFile: File,
        expectedRelease: LiveUpdateRelease,
        installed: InstalledAppDetails
    ): ApkVerificationResult {
        val inspected = inspectApkFile(context, downloadedFile, downloadedFile.name)
        val errors = mutableListOf<String>()

        if (!inspected.isValidArchive) {
            errors += (inspected.errorMessage ?: "Downloaded file is corrupted and cannot be installed.")
        }
        if (expectedRelease.apkSize > 0L && inspected.fileSizeBytes != expectedRelease.apkSize) {
            errors += "Incomplete APK download: expected ${expectedRelease.apkSize} bytes, got ${inspected.fileSizeBytes} bytes."
        }
        if (expectedRelease.apkSha256.isNotBlank() &&
            !inspected.apkSha256.equals(expectedRelease.apkSha256, ignoreCase = true)
        ) {
            errors += "APK integrity checksum verification failed."
        }
        if (inspected.packageName != installed.packageName) {
            errors += "Package mismatch: downloaded APK belongs to '${inspected.packageName}'."
        }
        if (inspected.versionCode != expectedRelease.versionCode) {
            errors += "Version mismatch: expected build ${expectedRelease.versionCode}, APK has ${inspected.versionCode}."
        }
        if (inspected.versionCode <= installed.versionCode) {
            errors += "Update build (${inspected.versionCode}) is not newer than installed build (${installed.versionCode})."
        }

        val matchedSigning = resolveMatchedSigningSha256(
            apkSigning = inspected.signingSha256Set,
            installedSigning = installed.signingSha256Set,
            expectedReleaseSigning = expectedRelease.signingSha256,
            errors = errors
        )

        return ApkVerificationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            matchedSigningSha256 = matchedSigning
        )
    }

    private fun resolveMatchedSigningSha256(
        apkSigning: Set<String>,
        installedSigning: Set<String>,
        expectedReleaseSigning: String,
        errors: MutableList<String>
    ): String {
        if (apkSigning.isEmpty()) {
            errors += "APK has no readable signing certificate."
            return ""
        }
        if (expectedReleaseSigning.isNotBlank() && apkSigning.none { it.equals(expectedReleaseSigning, ignoreCase = true) }) {
            errors += "APK signing certificate does not match the published release signature."
        }
        if (installedSigning.isNotEmpty()) {
            val common = apkSigning.firstOrNull { apkHash ->
                installedSigning.any { it.equals(apkHash, ignoreCase = true) }
            }
            if (common == null) {
                errors += "Incompatible APK signing certificate. This APK cannot update the installed STORE PRO build."
                return ""
            }
            return common.lowercase()
        }
        return apkSigning.first().lowercase()
    }

    @Suppress("DEPRECATION")
    fun extractSigningSha256(pkgInfo: PackageInfo): Set<String> {
        val result = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                val signers = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signers?.forEach { sig ->
                    result += sha256Hex(sig.toByteArray())
                }
            }
        }
        if (result.isEmpty()) {
            pkgInfo.signatures?.forEach { sig ->
                result += sha256Hex(sig.toByteArray())
            }
        }
        return result
    }

    fun extractSigningSha256FromApkZip(apkFile: File): Set<String> {
        val hashes = linkedSetOf<String>()
        runCatching {
            JarFile(apkFile).use { jar ->
                val cf = CertificateFactory.getInstance("X.509")
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val upper = entry.name.uppercase()
                    if (!entry.isDirectory &&
                        upper.startsWith("META-INF/") &&
                        (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))
                    ) {
                        jar.getInputStream(entry).use { stream ->
                            val certs = cf.generateCertificates(stream)
                            certs.filterIsInstance<X509Certificate>().forEach { cert ->
                                hashes += sha256Hex(cert.encoded)
                            }
                        }
                    }
                }
            }
        }
        return hashes
    }

    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16 * 1024)
            var read = input.read(buf)
            while (read != -1) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return bytesToHex(digest.digest())
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val cursorName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
        return cursorName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "storepro_release.apk"
    }
}
