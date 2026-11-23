package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.liveupdate.ApkVerificationResult
import com.example.data.liveupdate.LiveUpdateApkVerifier
import com.example.data.liveupdate.LiveUpdateController
import com.example.data.liveupdate.LiveUpdateInstallVerifier
import com.example.data.liveupdate.LiveUpdateVersionComparator
import com.example.data.model.HistoryApkDownloadStatus
import com.example.data.model.HistoryDownloadState
import com.example.data.model.InspectedApkMetadata
import com.example.data.model.InstalledAppDetails
import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateRelease
import com.example.data.model.UpdateHistoryRecord
import com.example.data.model.VersionComparisonOutcome
import com.example.data.repository.LiveUpdateRepository
import com.example.data.repository.StoreProRepository
import com.example.ui.home.HomeScreen
import com.example.ui.liveupdate.UpdateHistoryDetailScreen
import com.example.ui.liveupdate.UpdateHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.upload.UploadAppScreen
import com.example.ui.upload.UploadStudioTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveUpdateSystemTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_hidesLiveUpdateButtonFromRegularUsersAndOpensDirectly() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // Public / guest users MUST NOT see Admin-only STORE PRO LIVE UPDATE button or update prompts
        composeRule.onNodeWithTag("live_update_entry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("mandatory_update_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("app_update_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("UPDATE NOW", ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_lazy_column").assertIsDisplayed()

        // Dynamic installed version matches BuildConfig (v1.3 Build 4)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = LiveUpdateApkVerifier.readInstalledAppDetails(context)
        assertEquals(4L, installed.versionCode)
        assertEquals("1.3", installed.versionName)
        assertEquals(context.packageName, installed.packageName)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun homeScreen_hidesLiveUpdateButtonForBothUsersAndVerifiedAdmins() {
        var canAdmin by mutableStateOf(false)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MyApplicationTheme(darkTheme = true) {
                    HomeScreen(
                        applications = emptyList(),
                        userProfile = null,
                        canAccessAdminTools = canAdmin,
                        onOpenSearch = {},
                        onSelectApp = {},
                        onOpenProfile = {},
                        onOpenLiveUpdate = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_update_entry_button").assertDoesNotExist()
        composeRule.onNodeWithText("STORE PRO LIVE UPDATE", ignoreCase = true).assertDoesNotExist()

        // Admin verifies -> HomeScreen must STILL hide STORE PRO LIVE UPDATE (Admin accesses it via Admin Upload Studio)
        canAdmin = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_update_entry_button").assertDoesNotExist()
        composeRule.onNodeWithText("STORE PRO LIVE UPDATE", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    @Suppress("DEPRECATION")
    fun verifier_readsDynamicPackageManagerVersionAfterSimulatedInstallUpgrade() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPm = shadowOf(context.packageManager)

        val bumpedInfo = PackageInfo().apply {
            packageName = context.packageName
            versionName = "2.4.0"
            longVersionCode = 9L
            signatures = arrayOf(Signature(byteArrayOf(0x01, 0x02, 0x03, 0x04)))
        }
        shadowPm.installPackage(bumpedInfo)

        val details = LiveUpdateApkVerifier.readInstalledAppDetails(context)
        assertEquals(9L, details.versionCode)
        assertEquals("2.4.0", details.versionName)
        assertTrue(details.signingSha256Set.isNotEmpty())
    }

    @Test
    fun versionComparator_usesStrictVersionCodeComparisonAndIgnoresVersionName() {
        val baseRelease = LiveUpdateRelease(
            releaseId = "release_v2",
            versionName = "9.9.9",
            versionCode = 2L,
            packageName = "com.aistudio.storepro.vqxknp",
            apkDownloadUrl = "https://example.com/v2.apk",
            apkSize = 10_000_000L,
            releaseNotes = "Same build",
            releaseDate = "2026-11-21",
            published = true,
            releaseStatus = LiveReleaseStatus.PUBLISHED.name
        )

        assertTrue(LiveUpdateVersionComparator.compare(2L, baseRelease) is VersionComparisonOutcome.UpToDate)
        assertTrue(LiveUpdateVersionComparator.compare(5L, baseRelease) is VersionComparisonOutcome.UpToDate)

        val newerRelease = baseRelease.copy(releaseId = "release_v3", versionCode = 3L, versionName = "1.2")
        assertTrue(LiveUpdateVersionComparator.compare(2L, newerRelease) is VersionComparisonOutcome.UpdateAvailable)

        val draftRelease = newerRelease.copy(published = false, releaseStatus = LiveReleaseStatus.DRAFT.name)
        assertTrue(LiveUpdateVersionComparator.compare(2L, draftRelease) is VersionComparisonOutcome.UpToDate)
    }

    @Test
    fun repository_buildsPermanentNonOverwritingStoragePathsPerVersionAndBuild() {
        val v1Path = LiveUpdateRepository.permanentStoragePath("1.1", 2L, "STORE PRO v1.1.apk")
        val v2Path = LiveUpdateRepository.permanentStoragePath("1.2", 3L, "STORE PRO v1.2.apk")
        assertEquals("STORE_PRO/RELEASES/v1.1_b2/STORE_PRO_v1.1.apk", v1Path)
        assertEquals("STORE_PRO/RELEASES/v1.2_b3/STORE_PRO_v1.2.apk", v2Path)
        assertFalse(v1Path == v2Path)
    }

    @Test
    fun verifier_blocksMismatchedPackageVersionSigningCertAndDetectsHistoricalDowngrade() {
        val installed = InstalledAppDetails(
            appName = "STORE PRO",
            packageName = "com.aistudio.storepro.vqxknp",
            versionName = "1.2",
            versionCode = 3L,
            androidVersion = "15",
            apiLevel = 35,
            signingSha256Set = setOf("aabbccdd11223344")
        )
        val validCandidate = InspectedApkMetadata(
            fileName = "storepro_v4.apk",
            localTempPath = "/tmp/storepro_v4.apk",
            packageName = "com.aistudio.storepro.vqxknp",
            versionName = "1.3",
            versionCode = 4L,
            fileSizeBytes = 15_000_000L,
            apkSha256 = "deadbeef",
            signingSha256Set = setOf("aabbccdd11223344"),
            isValidArchive = true
        )

        assertTrue(
            LiveUpdateApkVerifier.verifyForAdminPublish(
                inspected = validCandidate,
                installed = installed,
                enteredVersionName = "1.3",
                enteredVersionCode = 4L,
                highestPublishedVersionCode = 3L
            ).isValid
        )
        assertFalse(
            LiveUpdateApkVerifier.verifyForAdminPublish(
                inspected = validCandidate.copy(packageName = "com.other.evilapp"),
                installed = installed,
                enteredVersionName = "1.3",
                enteredVersionCode = 4L,
                highestPublishedVersionCode = 3L
            ).isValid
        )
        assertFalse(
            LiveUpdateApkVerifier.verifyForAdminPublish(
                inspected = validCandidate.copy(signingSha256Set = setOf("ffffffff00000000")),
                installed = installed,
                enteredVersionName = "1.3",
                enteredVersionCode = 4L,
                highestPublishedVersionCode = 3L
            ).isValid
        )

        // Historical downgrade protection: installing older build 2 over installed build 3 triggers safe Android downgrade guard
        val inspectedOlder = validCandidate.copy(
            fileName = "storepro_v2.apk",
            versionName = "1.1",
            versionCode = 2L,
            fileSizeBytes = 14_000_000L
        )
        val olderRecord = UpdateHistoryRecord(
            historyId = "hist_v1.1_b2",
            versionName = "1.1",
            versionCode = 2L,
            packageName = "com.aistudio.storepro.vqxknp",
            apkFileSize = 14_000_000L,
            signingCertificateFingerprint = "aabbccdd11223344"
        )
        val downgradeCheck = LiveUpdateApkVerifier.verifyHistoricalMetadataCompatibility(inspectedOlder, olderRecord, installed)
        assertFalse(downgradeCheck.isValid)
        assertTrue(downgradeCheck.errors.any { it.contains("downgrade", ignoreCase = true) })

        // Historical upgrade (build 4 over installed build 3) passes cleanly
        val upgradeRecord = olderRecord.copy(
            historyId = "hist_v1.3_b4",
            versionName = "1.3",
            versionCode = 4L,
            apkFileSize = 15_000_000L
        )
        val upgradeCheck = LiveUpdateApkVerifier.verifyHistoricalMetadataCompatibility(validCandidate, upgradeRecord, installed)
        assertTrue(upgradeCheck.isValid)
    }

    @Test
    fun controller_downloadsHistoricalApkVerifiesIntegrityAndHandlesErrors(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LiveUpdateRepository(context)
        val zipApkBytes = buildMinimalZipApkBytes()
        val zipSha256 = sha256Hex(zipApkBytes)

        var serveMode = "VALID"
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = thread(start = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(start = true) {
                    client.use { sock ->
                        val reader = sock.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val out = sock.getOutputStream()
                        when (serveMode) {
                            "VALID" -> {
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ${zipApkBytes.size}\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(zipApkBytes)
                            }
                            else -> {
                                val html = "<!DOCTYPE html><html><body>Error 500</body></html>".toByteArray()
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(html)
                            }
                        }
                        out.flush()
                    }
                }
            }
        }

        try {
            val baseInstalled = InstalledAppDetails(
                appName = "STORE PRO",
                packageName = context.packageName,
                versionName = "1.1",
                versionCode = 2L,
                androidVersion = "15",
                apiLevel = 35,
                signingSha256Set = setOf("aabbccdd11223344")
            )
            val verifier = object : LiveUpdateInstallVerifier {
                override fun readInstalled(context: Context): InstalledAppDetails = baseInstalled
                override fun verifyDownloadedApk(
                    context: Context,
                    downloadedFile: File,
                    expectedRelease: LiveUpdateRelease,
                    installed: InstalledAppDetails
                ): ApkVerificationResult = ApkVerificationResult(true, emptyList(), "aabbccdd11223344")

                override fun verifyHistoricalApk(
                    context: Context,
                    downloadedFile: File,
                    historyRecord: UpdateHistoryRecord,
                    installed: InstalledAppDetails
                ): ApkVerificationResult = ApkVerificationResult(true, emptyList(), "aabbccdd11223344")
            }

            var hasInstallPerm = false
            val controller = LiveUpdateController(
                context = context,
                repository = repo,
                installVerifier = verifier,
                canInstallPackagesProvider = { hasInstallPerm }
            )

            val historyRec = UpdateHistoryRecord(
                historyId = "hist_v1.2_b3",
                versionName = "1.2",
                versionCode = 3L,
                packageName = context.packageName,
                apkDownloadUrl = "http://127.0.0.1:$port/STORE_PRO_v1.2_build3.apk",
                apkFileName = "STORE_PRO_v1.2_build3.apk",
                apkFileSize = zipApkBytes.size.toLong(),
                signingCertificateFingerprint = "aabbccdd11223344",
                releaseNotes = "Version 1.2 historical release"
            )

            // 1. HTML / invalid response fails cleanly
            serveMode = "HTML"
            controller.downloadHistoricalApk(historyRec, this)
            waitUntil {
                controller.historyDownloads.value[historyRec.historyId]?.status == HistoryApkDownloadStatus.FAILED
            }

            // 2. Valid archived APK download succeeds cleanly -> READY
            serveMode = "VALID"
            controller.downloadHistoricalApk(historyRec, this)
            waitUntil {
                controller.historyDownloads.value[historyRec.historyId]?.status == HistoryApkDownloadStatus.READY
            }
            val downloadedState = controller.historyDownloads.value[historyRec.historyId]!!
            assertEquals(100, downloadedState.progressPercent)
            assertNotNull(downloadedState.localApkPath)
            assertTrue(File(downloadedState.localApkPath!!).exists())

            hasInstallPerm = false
            val permIntent = controller.buildHistoricalInstallActionIntent(historyRec)
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permIntent?.action)

            hasInstallPerm = true
            val installIntent = controller.buildHistoricalInstallActionIntent(historyRec)
            assertEquals(Intent.ACTION_VIEW, installIntent?.action)
            assertEquals("application/vnd.android.package-archive", installIntent?.type)
        } finally {
            server.close()
            serverThread.interrupt()
        }
    }

    @Test
    fun uploadStudioPublishUpdateTab_andHistoryScreensRenderSearchFilterAndDetails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeRepo = StoreProRepository(context)
        val liveRepo = LiveUpdateRepository(context)
        val deviceRepo = com.example.data.repository.DeviceAndUpdateRepository(context)

        val records = listOf(
            UpdateHistoryRecord(
                historyId = "hist_1",
                versionName = "1.2",
                versionCode = 3L,
                packageName = context.packageName,
                apkDownloadUrl = "https://example.com/v1.2.apk",
                apkFileName = "STORE_PRO_v1.2_build3.apk",
                apkFileSize = 18_500_000L,
                releaseNotes = "Device dashboard + search RGB focus",
                mandatoryUpdate = true,
                apkAvailable = true,
                releaseTimestamp = 1_760_000_000_000L,
                adminEmail = "hasimsk12345678sk@gmail.com",
                previousVersion = "v1.1 (2)"
            ),
            UpdateHistoryRecord(
                historyId = "hist_2",
                versionName = "1.1",
                versionCode = 2L,
                packageName = context.packageName,
                apkDownloadUrl = "",
                apkFileName = "STORE_PRO_v1.1_build2.apk",
                apkFileSize = 16_000_000L,
                releaseNotes = "Initial dark theme polish",
                mandatoryUpdate = false,
                apkAvailable = false,
                releaseTimestamp = 1_750_000_000_000L,
                adminEmail = "hasimsk12345678sk@gmail.com",
                previousVersion = "v1.0 (1)"
            )
        )

        var selectedRec by mutableStateOf<UpdateHistoryRecord?>(null)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MyApplicationTheme(darkTheme = true) {
                    if (selectedRec == null) {
                        UploadAppScreen(
                            currentUserId = "admin_uid",
                            repository = storeRepo,
                            deviceUpdateRepository = deviceRepo,
                            liveUpdateRepository = liveRepo,
                            initialTab = UploadStudioTab.PUBLISH_UPDATE,
                            adminAllLiveReleases = emptyList(),
                            onOpenUpdateHistory = {},
                            onStatusMessage = {},
                            onBack = {},
                            onUploadSuccess = {},
                            onAdminLogout = {},
                            onUnauthorized = {}
                        )
                    } else {
                        UpdateHistoryDetailScreen(
                            record = selectedRec!!,
                            downloadState = HistoryDownloadState(historyId = selectedRec!!.historyId),
                            liveUpdateRepository = liveRepo,
                            onDownloadApk = {},
                            onCancelDownload = {},
                            onLaunchInstall = { Result.success(true) },
                            onBack = { selectedRec = null },
                            onUnauthorized = {},
                            onStatusMessage = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_tab_publish_app").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_tab_publish_update").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_open_history_button").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_version_code_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_version_name_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_apk_url_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_release_notes_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_mandatory_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_publish_immediately_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_submit_button").performScrollTo().assertIsDisplayed()

        // Test History list + filter + navigation to Detail Screen
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MyApplicationTheme(darkTheme = true) {
                    if (selectedRec == null) {
                        UpdateHistoryScreen(
                            records = records,
                            onSelectRecord = { selectedRec = it },
                            onBack = {},
                            onUnauthorized = {}
                        )
                    } else {
                        UpdateHistoryDetailScreen(
                            record = selectedRec!!,
                            downloadState = HistoryDownloadState(historyId = selectedRec!!.historyId),
                            liveUpdateRepository = liveRepo,
                            onDownloadApk = {},
                            onCancelDownload = {},
                            onLaunchInstall = { Result.success(true) },
                            onBack = { selectedRec = null },
                            onUnauthorized = {},
                            onStatusMessage = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_item_card_hist_1").assertIsDisplayed()
        composeRule.onNodeWithTag("update_history_lazy_column")
            .performScrollToNode(hasTestTag("history_item_card_hist_2"))
        composeRule.onNodeWithTag("history_item_card_hist_2").assertIsDisplayed()

        composeRule.onNodeWithTag("history_filter_MANDATORY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_item_card_hist_1").assertIsDisplayed()
        composeRule.onNodeWithTag("history_item_card_hist_2").assertDoesNotExist()

        composeRule.onNodeWithTag("history_item_card_hist_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_history_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_verify_apk_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("history_download_apk_button").performScrollTo().assertIsDisplayed()
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(25)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", predicate())
    }

    private fun buildMinimalZipApkBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("STOREPRO_MANIFEST_PAYLOAD".toByteArray())
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
