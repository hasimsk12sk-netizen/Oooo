package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.device.DeviceAuthorizationState
import com.example.data.device.DeviceIdentityProvider
import com.example.data.device.DeviceManager
import com.example.data.model.RegisteredDevice
import com.example.data.model.UserProfile
import com.example.data.repository.DeviceAndUpdateRepository
import com.example.notifications.StoreProMessagingService
import com.example.notifications.StoreProNotificationHelper
import com.example.ui.device.AccessBlockedScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SocialLinkLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceAndNotificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun deviceIdentityProvider_persistsUuidAndDetectsEmulatorsAndGenuineHardware() {
        val id1 = DeviceIdentityProvider.getOrCreateInstallationId(context)
        val id2 = DeviceIdentityProvider.getOrCreateInstallationId(context)
        assertTrue(id1.isNotBlank())
        assertEquals(id1, id2)
        assertFalse(id1.contains("@"))

        // Under Robolectric, physical-device guard must block automatic Firestore registration
        assertFalse(DeviceIdentityProvider.isGenuinePhysicalDevice())
        assertTrue(DeviceIdentityProvider.isRobolectricRuntime())

        // Known emulator / sdk_gphone64_arm64 fingerprints must be detected as non-genuine
        assertTrue(
            DeviceIdentityProvider.isEmulatorBuildProperties(
                fingerprint = "google/sdk_gphone64_arm64/emulator64_arm64:14/UE1A/123:userdebug/dev-keys",
                model = "sdk_gphone64_arm64",
                manufacturer = "Google",
                brand = "google",
                device = "emu64a",
                product = "sdk_gphone64_arm64",
                hardware = "ranchu",
                board = "goldfish_arm64",
                host = "abfarm"
            )
        )
        assertTrue(
            DeviceIdentityProvider.isEmulatorBuildProperties(
                fingerprint = "generic/aosp_x86/generic_x86:11/RSR1/123:eng/test-keys",
                model = "Android SDK built for x86",
                manufacturer = "unknown",
                brand = "generic",
                device = "generic_x86",
                product = "sdk_x86",
                hardware = "goldfish",
                board = "goldfish",
                host = "android-test"
            )
        )

        // Genuine physical hardware properties pass cleanly
        assertFalse(
            DeviceIdentityProvider.isEmulatorBuildProperties(
                fingerprint = "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S921BXXU1AWM9:user/release-keys",
                model = "SM-S921B",
                manufacturer = "samsung",
                brand = "samsung",
                device = "dm1q",
                product = "dm1qxxx",
                hardware = "s5e9945",
                board = "s5e9945",
                host = "21DJ6312"
            )
        )
        assertFalse(
            DeviceIdentityProvider.isEmulatorBuildProperties(
                fingerprint = "google/husky/husky:15/AP3A.241005.015/12366759:user/release-keys",
                model = "Pixel 8 Pro",
                manufacturer = "Google",
                brand = "google",
                device = "husky",
                product = "husky",
                hardware = "husky",
                board = "zuma",
                host = "abfarm-release-2004"
            )
        )
    }

    @Test
    fun registeredDevice_filtersFakeRecordsAndCalculatesUsageDaysAccurately() {
        val now = 1_750_000_000_000L
        val fakeRobo = RegisteredDevice(
            installationId = "sp_1",
            deviceName = "robolectric",
            deviceModel = "robolectric",
            manufacturer = "robolectric",
            brand = "robolectric"
        )
        val fakeGphone = RegisteredDevice(
            installationId = "sp_2",
            deviceName = "sdk_gphone64_arm64",
            deviceModel = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "google"
        )
        val realDevice = RegisteredDevice(
            installationId = "sp_real_pixel",
            deviceName = "Pixel 8 Pro",
            deviceModel = "Pixel 8 Pro",
            manufacturer = "Google",
            brand = "Google",
            androidVersion = "15",
            sdkVersion = 35,
            deviceType = "Phone",
            firstUsedAt = now - 24L * 86_400_000L,
            registeredAt = now - 24L * 86_400_000L,
            lastActiveAt = now
        )

        assertFalse(fakeRobo.isGenuineDeviceRecord())
        assertFalse(fakeGphone.isGenuineDeviceRecord())
        assertTrue(realDevice.isGenuineDeviceRecord())

        assertEquals("24 Days", realDevice.formattedUsageDuration(now))
        assertEquals(
            "0 Days (Today)",
            realDevice.copy(firstUsedAt = now - 3_600_000L).formattedUsageDuration(now)
        )
        assertEquals(
            "1 Day",
            realDevice.copy(firstUsedAt = now - 86_400_000L).formattedUsageDuration(now)
        )
        assertEquals(
            RegisteredDevice.NOT_AVAILABLE,
            realDevice.copy(firstUsedAt = 0L, registeredAt = 0L).formattedUsageDuration(now)
        )

        // Real RAM formatting & fallbacks
        assertEquals("8.00 GB", DeviceIdentityProvider.formatMemoryBytes(8L * 1024L * 1024L * 1024L))
        assertEquals("512 MB", DeviceIdentityProvider.formatMemoryBytes(512L * 1024L * 1024L))
        assertEquals(RegisteredDevice.NOT_AVAILABLE, DeviceIdentityProvider.formatMemoryBytes(0L))
    }

    @Test
    fun deduplicateDevices_mergesSamePhysicalDuplicatesWithoutMergingDifferentSameModelPhones() {
        val pkg = "com.aistudio.storepro.vqxknp"
        val phone1Canonical = DeviceIdentityProvider.computeCanonicalInstallationId(pkg, "android_id_phone_one_aaa")
        val phone1Fp = DeviceIdentityProvider.computeDeviceFingerprint(pkg, "android_id_phone_one_aaa")
        val phone2Canonical = DeviceIdentityProvider.computeCanonicalInstallationId(pkg, "android_id_phone_two_bbb")
        val phone2Fp = DeviceIdentityProvider.computeDeviceFingerprint(pkg, "android_id_phone_two_bbb")

        // Two physical phones of the exact same model (SM-S921B) MUST produce distinct canonical IDs
        assertNotEquals(phone1Canonical, phone2Canonical)
        assertNotEquals(phone1Fp, phone2Fp)

        val now = 1_760_000_000_000L
        val rawRecords = listOf(
            // Phone 1 legacy guest doc before reinstall/upgrade
            RegisteredDevice(
                installationId = "sp_legacy_uuid_111",
                deviceName = "Galaxy S24",
                deviceModel = "SM-S921B",
                manufacturer = "Samsung",
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_GUEST,
                status = RegisteredDevice.STATUS_ACTIVE,
                registeredAt = now - 10L * 86_400_000L,
                firstUsedAt = now - 10L * 86_400_000L,
                lastActiveAt = now - 3_600_000L
            ),
            // Phone 1 canonical doc after upgrade/reinstall + Gmail sign-in
            RegisteredDevice(
                installationId = phone1Canonical,
                legacyInstallationId = "sp_legacy_uuid_111",
                deviceFingerprint = phone1Fp,
                deviceName = "Galaxy S24",
                deviceModel = "SM-S921B",
                manufacturer = "Samsung",
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN,
                associatedUserId = "uid_user_1",
                associatedEmail = "user1@gmail.com",
                status = RegisteredDevice.STATUS_ACTIVE,
                registeredAt = now - 60_000L,
                firstUsedAt = now - 60_000L,
                lastActiveAt = now
            ),
            // Phone 2: completely separate physical Galaxy S24 (same model, different physical deviceFingerprint)
            RegisteredDevice(
                installationId = phone2Canonical,
                deviceFingerprint = phone2Fp,
                deviceName = "Galaxy S24",
                deviceModel = "SM-S921B",
                manufacturer = "Samsung",
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_GUEST,
                status = RegisteredDevice.STATUS_ACTIVE,
                registeredAt = now - 2_000L,
                firstUsedAt = now - 2_000L,
                lastActiveAt = now - 1_000L
            )
        )

        val deduped = DeviceAndUpdateRepository.deduplicateDevices(rawRecords)
        // Phone 1 duplicates merged -> 1; Phone 2 kept completely separate -> total 2
        assertEquals(2, deduped.size)
        val mergedPhone1 = deduped.first { it.installationId == phone1Canonical }
        assertEquals(now - 10L * 86_400_000L, mergedPhone1.firstUsedAt)
        assertEquals(now, mergedPhone1.lastActiveAt)
        assertEquals("user1@gmail.com", mergedPhone1.associatedEmail)
        assertTrue(mergedPhone1.isLoggedInAccount)

        val distinctPhone2 = deduped.first { it.installationId == phone2Canonical }
        assertFalse(distinctPhone2.isLoggedInAccount)
    }

    @Test
    fun deviceManager_blocksEmulatorSyncAndUpdatesRealDeviceThroughLifecycle() = runBlocking {
        // Non-genuine / emulator manager must never invoke Firestore registration
        var emulatorRegisterCalls = 0
        val emulatorManager = DeviceManager(
            installationId = "sp_emu",
            deviceName = "sdk_gphone64_arm64",
            deviceModel = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "google",
            androidVersion = "14",
            sdkVersion = 34,
            deviceType = "Phone",
            isGenuineDevice = false,
            versionCodeProvider = { 2L },
            versionNameProvider = { "1.1" },
            registerOrHeartbeatOp = { _, _, _, _, _, _, _, _, _, _ ->
                emulatorRegisterCalls++
                Result.success(RegisteredDevice())
            },
            fetchServerStatusOp = { Result.success(null) },
            observeStatusOp = { MutableStateFlow(null) }
        )
        val scope = TestScope(UnconfinedTestDispatcher())
        emulatorManager.start(scope)
        emulatorManager.checkNow(scope, force = true)
        delay(50)
        assertEquals(0, emulatorRegisterCalls)
        emulatorManager.stop()

        // Genuine device lifecycle: Guest -> Logged In -> Blocked -> Restored
        val liveFlow = MutableStateFlow<RegisteredDevice?>(null)
        val storedByInstallId = linkedMapOf<String, RegisteredDevice>()
        var currentUid: String? = null
        var serverBlocked = false

        val manager = DeviceManager(
            installationId = "sp_install_guest_01",
            deviceName = "Pixel 9",
            deviceModel = "Pixel 9 Pro",
            manufacturer = "Google",
            versionCodeProvider = { 2L },
            versionNameProvider = { "1.1" },
            registerOrHeartbeatOp = { id, name, model, mfr, code, vName ->
                val uid = currentUid
                val rec = RegisteredDevice(
                    installationId = id,
                    deviceName = name,
                    deviceModel = model,
                    manufacturer = mfr,
                    accountStatus = if (uid != null) {
                        RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN
                    } else {
                        RegisteredDevice.ACCOUNT_STATUS_GUEST
                    },
                    associatedUserId = uid,
                    status = if (serverBlocked) RegisteredDevice.STATUS_BLOCKED else RegisteredDevice.STATUS_ACTIVE,
                    appVersionCode = code,
                    appVersionName = vName
                )
                storedByInstallId[id] = rec
                liveFlow.value = rec
                Result.success(rec)
            },
            fetchServerStatusOp = { id ->
                Result.success(
                    storedByInstallId[id]?.copy(
                        status = if (serverBlocked) RegisteredDevice.STATUS_BLOCKED else RegisteredDevice.STATUS_ACTIVE
                    )
                )
            },
            observeStatusOp = { liveFlow }
        )

        manager.start(scope)
        waitUntil { storedByInstallId.containsKey("sp_install_guest_01") }
        val guestRecord = storedByInstallId["sp_install_guest_01"]!!
        assertEquals(1, storedByInstallId.size)
        assertEquals(RegisteredDevice.ACCOUNT_STATUS_GUEST, guestRecord.accountStatus)
        assertEquals("Google", guestRecord.manufacturer)
        assertNull(guestRecord.associatedUserId)
        assertTrue(manager.authState.value is DeviceAuthorizationState.Authorized)

        // User signs in with Gmail later -> same installationId updates without duplicate
        currentUid = "google_uid_abc"
        manager.checkNow(scope, force = true)
        waitUntil { storedByInstallId["sp_install_guest_01"]?.isLoggedInAccount == true }
        assertEquals(1, storedByInstallId.size)
        assertEquals("google_uid_abc", storedByInstallId["sp_install_guest_01"]?.associatedUserId)

        // Admin blocks device remotely -> realtime flow emits BLOCKED
        serverBlocked = true
        liveFlow.value = storedByInstallId["sp_install_guest_01"]?.copy(status = RegisteredDevice.STATUS_BLOCKED)
        waitUntil { manager.authState.value is DeviceAuthorizationState.Blocked }
        val blocked = manager.authState.value as DeviceAuthorizationState.Blocked
        assertEquals("Pixel 9", blocked.deviceName)
        assertEquals("Pixel 9 Pro", blocked.deviceModel)

        // Admin restores device access -> forced checkNow unblocks seamlessly
        serverBlocked = false
        liveFlow.value = storedByInstallId["sp_install_guest_01"]?.copy(status = RegisteredDevice.STATUS_ACTIVE)
        manager.checkNow(scope, force = true)
        waitUntil { manager.authState.value is DeviceAuthorizationState.Authorized }

        manager.stop()
    }

    @Test
    fun profileScreen_hidesDeviceTabForUsers_filtersFakes_maintainsConsistentCounts_andConfirmsDelete() {
        var isAdmin by mutableStateOf(false)
        var isServerVerified by mutableStateOf(false)
        val now = System.currentTimeMillis()
        val devices = listOf(
            RegisteredDevice(
                installationId = "dev_a",
                deviceName = "Galaxy S24",
                deviceModel = "SM-S921B",
                modelCode = "S921B",
                productName = "dm1qxxx",
                manufacturer = "Samsung",
                brand = "Samsung",
                androidVersion = "14",
                sdkVersion = 34,
                cpuArch = "arm64-v8a",
                ramInfo = "8.00 GB",
                totalRam = "8.00 GB",
                availableRam = "3.25 GB",
                screenResolution = "1080 x 2340",
                screenDensity = "420 dpi",
                connectionType = "Wi-Fi",
                deviceType = "Phone",
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_LOGGED_IN,
                associatedUserId = "uid_alice_1234",
                associatedEmail = "alice@example.com",
                status = RegisteredDevice.STATUS_ACTIVE,
                appVersionCode = 3,
                appVersionName = "1.2",
                installedAt = now - 5L * 86_400_000L,
                firstUsedAt = now - 5L * 86_400_000L,
                registeredAt = now - 5L * 86_400_000L,
                lastActiveAt = now,
                lastSyncAt = now
            ),
            RegisteredDevice(
                installationId = "dev_c",
                deviceName = "Pixel Tablet",
                deviceModel = "Tangorpro",
                manufacturer = "Google",
                brand = "Google",
                androidVersion = "15",
                sdkVersion = 35,
                deviceType = "Tablet",
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_GUEST,
                associatedUserId = null,
                status = RegisteredDevice.STATUS_ACTIVE,
                appVersionCode = 2,
                appVersionName = "1.1",
                firstUsedAt = now - 86_400_000L,
                registeredAt = now - 86_400_000L,
                lastActiveAt = now - 600_000L // Offline (> 3 min window)
            ),
            RegisteredDevice(
                installationId = "dev_b",
                deviceName = "Moto Edge",
                deviceModel = "XT2301",
                manufacturer = "Motorola",
                // Missing optional brand/android/sdk to verify "Not Available" fallback on legacy records
                accountStatus = RegisteredDevice.ACCOUNT_STATUS_GUEST,
                status = RegisteredDevice.STATUS_BLOCKED, // Blocked is also not currently active -> counts in Offline
                appVersionCode = 1,
                appVersionName = "1.0",
                registeredAt = now - 3L * 86_400_000L,
                lastActiveAt = now - 600_000L
            ),
            // Fake / emulator records that MUST be excluded from UI & counts
            RegisteredDevice(
                installationId = "dev_fake_robo",
                deviceName = "robolectric",
                deviceModel = "robolectric",
                manufacturer = "robolectric",
                status = RegisteredDevice.STATUS_ACTIVE,
                lastActiveAt = now
            ),
            RegisteredDevice(
                installationId = "dev_fake_emu",
                deviceName = "sdk_gphone64_arm64",
                deviceModel = "sdk_gphone64_arm64",
                manufacturer = "Google",
                status = RegisteredDevice.STATUS_ACTIVE,
                lastActiveAt = now
            )
        )
        var toggledId: String? = null
        var deletedId: String? = null
        var refreshCalls = 0

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ProfileScreen(
                    isSignedIn = isAdmin,
                    isAdminAuthorized = isAdmin,
                    isServerAdminVerified = isServerVerified,
                    registeredDevices = devices,
                    userProfile = if (isAdmin) UserProfile("u1", "hasimsk12345678sk@gmail.com", "Admin") else null,
                    myUploads = emptyList(),
                    downloads = emptyList(),
                    savedApps = emptyList(),
                    onSelectApp = {},
                    onEditApp = {},
                    onDeleteApp = {},
                    onOpenUpload = {},
                    onGoogleSignIn = {},
                    onAdminLogout = {},
                    onSignOut = {},
                    onToggleDeviceBlock = { toggledId = it.installationId },
                    onDeleteDevice = { deletedId = it },
                    onRefreshDevices = { refreshCalls++ }
                )
            }
        }

        // Regular user MUST NOT see Device tab
        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("profile_tab_my_uploads"))
        composeRule.onNodeWithTag("profile_tab_my_uploads").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_tab_device").assertDoesNotExist()

        // Client flag without server verification MUST NOT expose Device tab
        isAdmin = true
        isServerVerified = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_tab_device").assertDoesNotExist()

        // Verified Administrator gets Device tab and real-time dashboard with dedicated REFRESH button
        isServerVerified = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("profile_tab_device"))
        composeRule.onNodeWithTag("profile_tab_device").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("device_dashboard_summary"))
        composeRule.onNodeWithTag("device_dashboard_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("device_live_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("device_refresh_button").assertIsDisplayed().performClick()
        assertEquals(1, refreshCalls)

        // Consistent math excluding fake/emulator rows: Total(3) = Active(1) + Offline(2), Blocked(1)
        composeRule.onNodeWithTag("device_total_count").assertTextContains("3")
        composeRule.onNodeWithTag("device_active_count").assertTextContains("1")
        composeRule.onNodeWithTag("device_offline_count").assertTextContains("2")
        composeRule.onNodeWithTag("device_blocked_count").assertTextContains("1")

        // Fake / emulator devices must never be displayed
        composeRule.onNodeWithTag("device_card_dev_fake_robo").assertDoesNotExist()
        composeRule.onNodeWithTag("device_card_dev_fake_emu").assertDoesNotExist()

        // Expand genuine device details & verify extended hardware + network + email metadata
        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("device_card_dev_a"))
        composeRule.onNodeWithText("Galaxy S24").assertIsDisplayed()
        composeRule.onNodeWithText("SM-S921B").assertIsDisplayed()
        composeRule.onNodeWithTag("device_details_toggle_dev_a").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_details_panel_dev_a").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Android 14").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("API 34").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("arm64-v8a").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("8.00 GB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3.25 GB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("alice@example.com").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("5 Days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("dev_a").performScrollTo().assertIsDisplayed()

        // Block action
        composeRule.onNodeWithTag("device_block_toggle_dev_a").performScrollTo().performClick()
        assertEquals("dev_a", toggledId)

        // Legacy device displays "Not Available" for missing fields & Delete requires confirmation dialog
        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("device_card_dev_b"))
        composeRule.onNodeWithTag("device_details_toggle_dev_b").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_details_panel_dev_b").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("device_delete_dev_b").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Must NOT delete yet before confirming
        assertNull(deletedId)
        composeRule.onNodeWithTag("device_delete_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("device_delete_cancel_button").performClick()
        composeRule.waitForIdle()
        assertNull(deletedId)

        // Confirm delete
        composeRule.onNodeWithTag("device_delete_dev_b").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_delete_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("dev_b", deletedId)

        // Search by brand/manufacturer or account status
        composeRule.onNodeWithTag("profile_lazy_column").performScrollToNode(hasTestTag("device_search_input"))
        composeRule.onNodeWithTag("device_search_input").performTextInput("Google")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_card_dev_c").assertIsDisplayed()
        composeRule.onNodeWithTag("device_card_dev_a").assertDoesNotExist()
        // Fake Google emulator must still be excluded during search
        composeRule.onNodeWithTag("device_card_dev_fake_emu").assertDoesNotExist()

        composeRule.onNodeWithTag("device_search_input").performTextClearance()
        composeRule.onNodeWithTag("device_search_input").performTextInput("Logged In")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_card_dev_a").assertIsDisplayed()
        composeRule.onNodeWithTag("device_card_dev_c").assertDoesNotExist()
    }

    @Test
    fun profileHeader_displaysWhatsAppAndTelegramButtonsSeparatelyWithoutBreakingAvatar() {
        var whatsAppClicks = 0
        var telegramClicks = 0

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ProfileScreen(
                    isSignedIn = false,
                    isAdminAuthorized = false,
                    isServerAdminVerified = false,
                    userProfile = null,
                    myUploads = emptyList(),
                    downloads = emptyList(),
                    savedApps = emptyList(),
                    onSelectApp = {},
                    onEditApp = {},
                    onDeleteApp = {},
                    onOpenUpload = {},
                    onGoogleSignIn = {},
                    onAdminLogout = {},
                    onSignOut = {},
                    onWhatsAppClick = { whatsAppClicks++ },
                    onTelegramClick = { telegramClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_whatsapp_button").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_avatar_box").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_telegram_button").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_whatsapp_button").performClick()
        assertEquals(1, whatsAppClicks)
        assertEquals(0, telegramClicks)

        composeRule.onNodeWithTag("profile_telegram_button").performClick()
        assertEquals(1, whatsAppClicks)
        assertEquals(1, telegramClicks)
    }

    @Test
    fun socialLinkLauncher_usesExactCommunityUrlsAndAppFirstWithBrowserFallback() {
        assertEquals(
            "https://chat.whatsapp.com/KZPB5BRZNpJ1wNIL3JawJ8?s=cl&p=a&iam=2",
            SocialLinkLauncher.WHATSAPP_GROUP_URL
        )
        assertEquals(
            "https://t.me/hasimapk",
            SocialLinkLauncher.TELEGRAM_CHANNEL_URL
        )

        // With no apps installed, always falls back to generic BROWSABLE VIEW intent without throwing
        val fallbackCandidates = SocialLinkLauncher.buildLaunchCandidates(
            context = context,
            url = SocialLinkLauncher.TELEGRAM_CHANNEL_URL,
            preferredPackages = SocialLinkLauncher.TELEGRAM_PACKAGES
        )
        assertTrue(fallbackCandidates.isNotEmpty())
        assertEquals(Intent.ACTION_VIEW, fallbackCandidates.last().action)
        assertEquals(SocialLinkLauncher.TELEGRAM_CHANNEL_URL, fallbackCandidates.last().dataString)
        assertNull(fallbackCandidates.last().`package`)

        // Register a mock handler for com.whatsapp to verify native app intent takes priority first
        val shadowPm = shadowOf(context.packageManager)
        val whatsAppActivity = android.content.ComponentName("com.whatsapp", "com.whatsapp.Main")
        @Suppress("DEPRECATION")
        shadowPm.addActivityIfNotPresent(whatsAppActivity)
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("https")
            addDataAuthority("chat.whatsapp.com", null)
        }
        @Suppress("DEPRECATION")
        shadowPm.addIntentFilterForActivity(whatsAppActivity, filter)

        val whatsAppCandidates = SocialLinkLauncher.buildLaunchCandidates(
            context = context,
            url = SocialLinkLauncher.WHATSAPP_GROUP_URL,
            preferredPackages = SocialLinkLauncher.WHATSAPP_PACKAGES
        )
        assertTrue(whatsAppCandidates.size >= 2)
        assertEquals("com.whatsapp", whatsAppCandidates.first().`package`)
        assertEquals(SocialLinkLauncher.WHATSAPP_GROUP_URL, whatsAppCandidates.first().dataString)
        assertNull(whatsAppCandidates.last().`package`)
    }

    @Test
    fun accessBlockedScreen_displaysNonCrashingMessageAndRecheckAction() {
        var recheckClicked = 0
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AccessBlockedScreen(
                    deviceName = "Primary Phone",
                    deviceModel = "Pixel 8 Pro",
                    isRefreshing = false,
                    onCheckAuthorization = { recheckClicked++ }
                )
            }
        }

        composeRule.onNodeWithTag("access_blocked_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("blocked_screen_title").assertTextContains("Access Blocked")
        composeRule.onNodeWithText("Primary Phone").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 8 Pro").assertIsDisplayed()
        composeRule.onNodeWithTag("blocked_recheck_button").assertIsDisplayed().performClick()
        assertEquals(1, recheckClicked)
    }

    @Test
    fun notificationHelper_deduplicatesAndExtractsDeepLinkAppIdAcrossIntentForms() {
        val uniqueEvent = "evt_${System.nanoTime()}"
        assertTrue(StoreProNotificationHelper.markAndCheckShouldNotify(context, uniqueEvent))
        // Second trigger with same eventId must be deduplicated
        assertFalse(StoreProNotificationHelper.markAndCheckShouldNotify(context, uniqueEvent))

        val clickIntent = StoreProNotificationHelper.buildOpenAppDetailsIntent(context, "app_12345")
        assertEquals("app_12345", StoreProNotificationHelper.extractTargetAppId(clickIntent))

        val backgroundFcmIntent = Intent().apply {
            putExtra(StoreProNotificationHelper.KEY_FCM_APP_ID, "app_bg_99")
        }
        assertEquals("app_bg_99", StoreProNotificationHelper.extractTargetAppId(backgroundFcmIntent))
        assertNotEquals(clickIntent.action, backgroundFcmIntent.action)
    }

    @Test
    fun messagingService_handlesNewAppPublishedDataPayloadSafely() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES
        )
        val hasMessagingService = pkgInfo.services?.any {
            it.name == StoreProMessagingService::class.java.name
        } == true
        assertTrue("StoreProMessagingService must be registered in AndroidManifest", hasMessagingService)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val eventId = "evt_service_${System.nanoTime()}"
        val payload = mapOf(
            "type" to "new_app_published",
            "appId" to "app_xyz_777",
            "appName" to "Nova Player",
            "eventId" to eventId
        )

        // When notification permission is denied, gracefully skips posting without crashing
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(StoreProMessagingService.handleIncomingDataPayload(context, payload))

        // When permission is granted, posts once and deduplicates repeated eventId
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(StoreProMessagingService.handleIncomingDataPayload(context, payload))
        assertFalse(StoreProMessagingService.handleIncomingDataPayload(context, payload))
        assertFalse(StoreProMessagingService.handleIncomingDataPayload(context, mapOf("appId" to "")))
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Timed out waiting for expected condition")
            }
            delay(20)
        }
    }
}
