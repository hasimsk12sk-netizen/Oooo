package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoaderFactory
import com.example.data.model.ApplicationItem
import com.example.data.model.RegisteredDevice
import com.example.ui.components.LocalSharedNeonPhase
import com.example.ui.components.ProvideSharedNeonGlowClock
import com.example.ui.home.HomeScreen
import com.example.ui.profile.filterGenuineDevices
import com.example.ui.profile.prepareGenuineDevices
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.upload.UniversalImageLoader
import com.example.util.DisplayRefreshRateOptimizer
import com.example.util.DisplayRefreshRateOptimizer.DisplayModeCandidate
import com.example.util.StoreProNetworkClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceOptimizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buildConfig_hasUpdatedVersion13AndBuild4() {
        assertEquals(4, BuildConfig.VERSION_CODE)
        assertEquals("1.3", BuildConfig.VERSION_NAME)
        assertEquals("com.aistudio.storepro.vqxknp", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun displayRefreshRateOptimizer_selectsHighestNativeResolutionRateAndRespectsBatterySaver() {
        val modes = listOf(
            DisplayModeCandidate(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeCandidate(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f),
            DisplayModeCandidate(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
            DisplayModeCandidate(modeId = 4, physicalWidth = 720, physicalHeight = 1600, refreshRate = 144f)
        )

        // Picks 120Hz at native 1080x2400 (ignores 144Hz 720x1600 resolution mismatch)
        val best120 = DisplayRefreshRateOptimizer.selectBestDisplayModeId(
            currentModeId = 1,
            currentWidth = 1080,
            currentHeight = 2400,
            supportedModes = modes,
            isPowerSaveMode = false
        )
        assertEquals(3, best120)

        // When Battery Saver is enabled, resets override to 0 (system battery-saving mode)
        val batterySaveMode = DisplayRefreshRateOptimizer.selectBestDisplayModeId(
            currentModeId = 3,
            currentWidth = 1080,
            currentHeight = 2400,
            supportedModes = modes,
            isPowerSaveMode = true
        )
        assertEquals(0, batterySaveMode)
    }

    @Test
    fun networkClient_sharesSingleConnectionPoolAndDispatcherAcrossClients() {
        val base = StoreProNetworkClient.baseClient
        val images = StoreProNetworkClient.imageClient
        val downloads = StoreProNetworkClient.downloadClient
        val probe = StoreProNetworkClient.fastProbeClient

        assertSame(base.connectionPool, images.connectionPool)
        assertSame(base.connectionPool, downloads.connectionPool)
        assertSame(base.connectionPool, probe.connectionPool)

        assertSame(base.dispatcher, images.dispatcher)
        assertSame(base.dispatcher, downloads.dispatcher)
        assertSame(base.dispatcher, probe.dispatcher)
    }

    @Test
    fun storeProApplication_implementsImageLoaderFactoryWithSharedLoader() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app is ImageLoaderFactory)
        val factoryLoader = (app as ImageLoaderFactory).newImageLoader()
        val sharedLoader = UniversalImageLoader.get(app)
        assertSame(sharedLoader, factoryLoader)
    }

    @Test
    fun deviceRegistryHelpers_memoizeGenuineDevicesAndFilterCaseInsensitively() {
        val validOne = RegisteredDevice(
            installationId = "inst_1",
            deviceName = "Pixel 9 Pro",
            deviceModel = "Pixel 9 Pro",
            manufacturer = "Google",
            brand = "google",
            associatedEmail = "Hasim@example.com",
            lastActiveAt = 2_000L
        )
        val validTwo = RegisteredDevice(
            installationId = "inst_2",
            deviceName = "Galaxy S24",
            deviceModel = "SM-S921B",
            manufacturer = "Samsung",
            brand = "samsung",
            lastActiveAt = 5_000L
        )
        val invalidBlank = RegisteredDevice(
            installationId = "",
            deviceName = "",
            deviceModel = ""
        )

        val prepared = prepareGenuineDevices(listOf(validOne, validTwo, validOne.copy(lastActiveAt = 1_000L), invalidBlank))
        assertEquals(listOf("inst_2", "inst_1"), prepared.map { it.installationId })

        val filtered = filterGenuineDevices(prepared, "HASIM")
        assertEquals(1, filtered.size)
        assertEquals("inst_1", filtered.first().installationId)
    }

    @Test
    fun sharedNeonGlowClock_providesPhaseAndComposesHomeScreenCleanly() {
        var capturedPhase: Any? = null
        val apps = listOf(
            ApplicationItem(
                appId = "app_1",
                appName = "Alpha Tool",
                developerName = "Dev Studio",
                version = "1.0",
                downloads = 500L,
                averageRating = 4.9
            )
        )

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ProvideSharedNeonGlowClock {
                    capturedPhase = LocalSharedNeonPhase.current
                    HomeScreen(
                        applications = apps,
                        userProfile = null,
                        onOpenSearch = {},
                        onSelectApp = {},
                        onOpenProfile = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertNotNull(capturedPhase)
        composeRule.onNodeWithTag("featured_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lazy_column").assertIsDisplayed()
    }
}
