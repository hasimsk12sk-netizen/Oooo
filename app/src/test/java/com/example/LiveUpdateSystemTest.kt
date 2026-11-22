package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.liveupdate.ApkVerificationResult
import com.example.data.liveupdate.LiveUpdateApkVerifier
import com.example.data.liveupdate.LiveUpdateController
import com.example.data.liveupdate.LiveUpdateInstallVerifier
import com.example.data.liveupdate.LiveUpdateVersionComparator
import com.example.data.model.InspectedApkMetadata
import com.example.data.model.InstalledAppDetails
import com.example.data.model.LiveReleaseStatus
import com.example.data.model.LiveUpdateDashboardState
import com.example.data.model.LiveUpdateRelease
import com.example.data.model.LiveUpdateStatus
import com.example.data.model.VersionComparisonOutcome
import com.example.data.repository.LiveUpdateRepository
import com.example.ui.liveupdate.AdminLiveUpdateScreen
import com.example.ui.liveupdate.LiveUpdateDashboardScreen
import com.example.ui.theme.MyApplicationTheme
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
    fun mainActivity_showsNewLiveUpdateButton_andNavigatesToDashboardForRegularUser() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // New Live Update button is visible immediately on Home for every user
        composeRule.onNodeWithTag("live_update_entry_button").assertIsDisplayed()
        composeRule.onNodeWithText("STORE PRO LIVE UPDATE").assertIsDisplayed()

        // Legacy self-update components are permanently gone
        composeRule.onNodeWithTag("mandatory_update_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("app_update_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("UPDATE NOW", ignoreCase = true).assertDoesNotExist()

        // Tap STORE PRO LIVE UPDATE -> opens Live Update Dashboard
        composeRule.onNodeWithTag("live_update_entry_button").performClick()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        composeRule.onNodeWithTag("live_update_dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_installed_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_latest_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_comparison_table").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_release_history").performScrollTo().assertIsDisplayed()

        // Dynamic installed version values match build metadata (never hardcoded)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = LiveUpdateApkVerifier.readInstalledAppDetails(context)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), installed.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, installed.versionName)
        assertEquals(context.packageName, installed.packageName)

        // Ordinary users MUST NOT see Admin Release Management button
        composeRule.onNodeWithTag("open_admin_live_update_button").assertDoesNotExist()

        // Navigate back to Home cleanly
        composeRule.onNodeWithTag("live_update_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun dashboard_rendersAdminStudioButtonOnlyWhenAuthorized_onTabletLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = LiveUpdateApkVerifier.readInstalledAppDetails(context)
        val latest = LiveUpdateRelease(
            releaseId = "release_v3",
            versionName = "1.2",
            versionCode = 3L,
            packageName = installed.packageName,
            apkDownloadUrl = "https://example.com/storepro_v3.apk",
            apkSize = 25_000_000L,
            releaseNotes = "Performance upgrades and Live Update center.",
            releaseDate = "2026-11-21",
            published = true,
            releaseStatus = LiveReleaseStatus.PUBLISHED.name
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MyApplicationTheme(darkTheme = true) {
                    LiveUpdateDashboardScreen(
                        state = LiveUpdateDashboardState(
                            installed = installed,
                            latestRelease = latest,
                            publishedHistory = listOf(latest),
                            status = LiveUpdateStatus.UPDATE_AVAILABLE,
                            statusDetail = "Build 3 (1.2) is available."
                        ),
                        canAccessAdminTools = true,
                        onBack = {},
                        onRefresh = {},
                        onStartDownload = {},
                        onCancelDownload = {},
                        onRetryDownload = {},
                        onInstallUpdate = {},
                        onOpenAdminManager = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_update_installed_card").assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_latest_card").assertIsDisplayed()
        composeRule.onNodeWithTag("live_update_status_text").assertTextEquals("New update available.")
        composeRule.onNodeWithTag("open_admin_live_update_button").performScrollTo().assertIsDisplayed()
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
            versionName = "9.9.9", // Higher versionName string MUST NOT trigger update when versionCode is equal
            versionCode = 2L,
            packageName = "com.aistudio.storepro.vqxknp",
            apkDownloadUrl = "https://example.com/v2.apk",
            apkSize = 10_000_000L,
            releaseNotes = "Same build",
            releaseDate = "2026-11-21",
            published = true,
            releaseStatus = LiveReleaseStatus.PUBLISHED.name
        )

        // Equal build (2 == 2) -> UpToDate
        val equalOutcome = LiveUpdateVersionComparator.compare(2L, baseRelease)
        assertTrue(equalOutcome is VersionComparisonOutcome.UpToDate)

        // Higher installed build than server (5 > 2) -> Never offer older APK
        val downgradeOutcome = LiveUpdateVersionComparator.compare(5L, baseRelease)
        assertTrue(downgradeOutcome is VersionComparisonOutcome.UpToDate)

        // Newer published build (2 < 3) -> UpdateAvailable
        val newerRelease = baseRelease.copy(releaseId = "release_v3", versionCode = 3L, versionName = "1.2")
        val upgradeOutcome = LiveUpdateVersionComparator.compare(2L, newerRelease)
        assertTrue(upgradeOutcome is VersionComparisonOutcome.UpdateAvailable)

        // Unpublished draft MUST NOT appear available to users
        val draftRelease = newerRelease.copy(published = false, releaseStatus = LiveReleaseStatus.DRAFT.name)
        val draftOutcome = LiveUpdateVersionComparator.compare(2L, draftRelease)
        assertTrue(draftOutcome is VersionComparisonOutcome.UpToDate)
    }

    @Test
    fun verifier_blocksMismatchedPackageVersionSigningCertAndInvalidApkOnAdminPublish() {
        val installed = InstalledAppDetails(
            appName = "STORE PRO",
            packageName = "com.aistudio.storepro.vqxknp",
            versionName = "1.1",
            versionCode = 2L,
            androidVersion = "15",
            apiLevel = 35,
            signingSha256Set = setOf("aabbccdd11223344")
        )
        val validCandidate = InspectedApkMetadata(
            fileName = "storepro_v3.apk",
            localTempPath = "/tmp/storepro_v3.apk",
            packageName = "com.aistudio.storepro.vqxknp",
            versionName = "1.2",
            versionCode = 3L,
            fileSizeBytes = 15_000_000L,
            apkSha256 = "deadbeef",
            signingSha256Set = setOf("aabbccdd11223344"),
            isValidArchive = true
        )

        // 1. Valid candidate passes cleanly
        val okResult = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate,
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 3L,
            highestPublishedVersionCode = 2L
        )
        assertTrue(okResult.isValid)
        assertEquals("aabbccdd11223344", okResult.matchedSigningSha256)

        // 2. Wrong package name blocked
        val wrongPkg = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate.copy(packageName = "com.other.evilapp"),
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 3L,
            highestPublishedVersionCode = 2L
        )
        assertFalse(wrongPkg.isValid)

        // 3. Version Code mismatch against APK blocked
        val wrongCode = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate,
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 4L,
            highestPublishedVersionCode = 2L
        )
        assertFalse(wrongCode.isValid)

        // 4. Same or older Version Code than already published blocked
        val olderCode = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate.copy(versionCode = 2L),
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 2L,
            highestPublishedVersionCode = 2L
        )
        assertFalse(olderCode.isValid)

        // 5. Incompatible signing certificate blocked
        val wrongCert = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate.copy(signingSha256Set = setOf("ffffffff00000000")),
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 3L,
            highestPublishedVersionCode = 2L
        )
        assertFalse(wrongCert.isValid)

        // 6. Corrupted / invalid APK archive blocked
        val corrupted = LiveUpdateApkVerifier.verifyForAdminPublish(
            inspected = validCandidate.copy(isValidArchive = false, errorMessage = "Corrupted archive"),
            installed = installed,
            enteredVersionName = "1.2",
            enteredVersionCode = 3L,
            highestPublishedVersionCode = 2L
        )
        assertFalse(corrupted.isValid)
    }

    @Test
    fun controller_downloadsVerifiedApkTracksProgressBuildsInstallIntentAndHandlesErrors(): Unit = runBlocking {
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
                            "HTML_ERROR" -> {
                                val html = "<!DOCTYPE html><html><body>Error 500</body></html>".toByteArray()
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(html)
                            }
                            "CORRUPT_BYTES" -> {
                                val bad = "NOT_A_ZIP_OR_APK_BINARY_PAYLOAD".toByteArray()
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ${bad.size}\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(bad)
                            }
                            else -> {
                                val msg = "Not Found".toByteArray()
                                val header = "HTTP/1.1 404 Not Found\r\nContent-Length: ${msg.size}\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(msg)
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
            var verifyShouldPass = true
            val verifier = object : LiveUpdateInstallVerifier {
                override fun readInstalled(context: Context): InstalledAppDetails = baseInstalled
                override fun verifyDownloadedApk(
                    context: Context,
                    downloadedFile: File,
                    expectedRelease: LiveUpdateRelease,
                    installed: InstalledAppDetails
                ): ApkVerificationResult {
                    return if (verifyShouldPass) {
                        ApkVerificationResult(true, emptyList(), "aabbccdd11223344")
                    } else {
                        ApkVerificationResult(false, listOf("Incompatible APK signing certificate."))
                    }
                }
            }

            var hasInstallPerm = false
            val controller = LiveUpdateController(
                context = context,
                repository = repo,
                installVerifier = verifier,
                canInstallPackagesProvider = { hasInstallPerm }
            )

            val publishedV3 = LiveUpdateRelease(
                releaseId = "release_v3",
                versionName = "1.2",
                versionCode = 3L,
                packageName = context.packageName,
                apkDownloadUrl = "http://127.0.0.1:$port/storepro_v3.apk",
                apkSize = zipApkBytes.size.toLong(),
                releaseNotes = "Live update v1.2",
                releaseDate = "2026-11-21",
                published = true,
                releaseStatus = LiveReleaseStatus.PUBLISHED.name,
                apkSha256 = zipSha256,
                signingSha256 = "aabbccdd11223344"
            )

            // Publish release into controller -> UPDATE_AVAILABLE
            controller.applyPublishedReleases(listOf(publishedV3))
            assertEquals(LiveUpdateStatus.UPDATE_AVAILABLE, controller.state.value.status)

            // 1. HTML error fails safely -> FAILED ("Update failed.")
            serveMode = "HTML_ERROR"
            controller.startDownload(this)
            waitUntil { controller.state.value.status == LiveUpdateStatus.FAILED }
            assertEquals("Update failed.", controller.state.value.status.label)
            assertTrue(controller.state.value.errorMessage?.contains("webpage", ignoreCase = true) == true)

            // 2. Corrupted binary fails safely -> FAILED
            serveMode = "CORRUPT_BYTES"
            controller.retryDownload(this)
            waitUntil { controller.state.value.status == LiveUpdateStatus.FAILED }
            assertTrue(controller.state.value.errorMessage?.contains("corrupted", ignoreCase = true) == true)

            // 3. Incompatible signature fails verification even after valid download -> FAILED
            serveMode = "VALID"
            verifyShouldPass = false
            controller.retryDownload(this)
            waitUntil { controller.state.value.status == LiveUpdateStatus.FAILED }
            assertTrue(controller.state.value.errorMessage?.contains("certificate", ignoreCase = true) == true)

            // 4. Valid signed download succeeds -> READY_TO_INSTALL (never claims "Installed")
            verifyShouldPass = true
            controller.retryDownload(this)
            waitUntil { controller.state.value.status == LiveUpdateStatus.READY_TO_INSTALL }
            val readyState = controller.state.value
            assertEquals(100, readyState.downloadPercent)
            assertNotNull(readyState.downloadedApkPath)
            assertTrue(File(readyState.downloadedApkPath!!).exists())

            // Unknown sources permission needed on API 26+
            hasInstallPerm = false
            val permIntent = controller.buildInstallActionIntent()
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permIntent?.action)

            // When permission granted -> ACTION_VIEW FileProvider URI
            hasInstallPerm = true
            val installIntent = controller.buildInstallActionIntent()
            assertEquals(Intent.ACTION_VIEW, installIntent?.action)
            assertTrue(installIntent?.data?.scheme == "content")
            assertEquals("${context.packageName}.provider", installIntent?.data?.authority)
            assertEquals("application/vnd.android.package-archive", installIntent?.type)
        } finally {
            server.close()
            serverThread.interrupt()
        }
    }

    @Test
    fun adminLiveUpdateScreen_rendersAllTenFieldsCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val liveRepo = LiveUpdateRepository(context)
        val deviceRepo = com.example.data.repository.DeviceAndUpdateRepository(context)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MyApplicationTheme(darkTheme = true) {
                    AdminLiveUpdateScreen(
                        allReleases = emptyList(),
                        liveUpdateRepository = liveRepo,
                        deviceUpdateRepository = deviceRepo,
                        onBack = {},
                        onUnauthorized = {},
                        onStatusMessage = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_live_version_name_input").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_version_code_input").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_pick_apk_button").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_apk_url_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_apk_size_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_release_notes_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_release_date_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_status_published").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_status_draft").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_submit_button").performScrollTo().assertIsDisplayed()
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
