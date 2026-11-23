package com.example

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ApplicationItem
import com.example.data.model.DownloadItem
import com.example.data.model.SavedAppItem
import com.example.data.model.UserProfile
import com.example.ui.components.RgbNeonPalette
import com.example.ui.home.HomeScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.search.SearchScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RgbCardGlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleApps(): List<ApplicationItem> = listOf(
        ApplicationItem(
            appId = "app_vn",
            appName = "VN - Video Editor",
            developerName = "Ubiquiti Labs",
            version = "2.2.1",
            downloads = 9500L,
            averageRating = 4.9
        ),
        ApplicationItem(
            appId = "app_vlc",
            appName = "VLC Media Player",
            developerName = "VideoLAN",
            version = "3.5.4",
            downloads = 6200L,
            averageRating = 4.8
        ),
        ApplicationItem(
            appId = "app_nova",
            appName = "Nova Launcher",
            developerName = "Nova Studio",
            version = "8.0.5",
            downloads = 4100L,
            averageRating = 4.7
        ),
        ApplicationItem(
            appId = "app_notes",
            appName = "Zen Notes Pro",
            developerName = "Zenith Apps",
            version = "1.4.0",
            downloads = 1200L,
            averageRating = 4.6
        )
    )

    @Test
    fun rgbNeonPalette_providesDistinctMultiStopPalettesAndRespectsReducedMotion() {
        assertTrue(RgbNeonPalette.palettes.size >= 4)
        RgbNeonPalette.palettes.forEach { stops ->
            assertTrue("Each neon palette should contain multiple RGB stops", stops.size >= 3)
        }
        val chosen = (1..30).map { RgbNeonPalette.paletteFor("app_$it") }.toSet()
        assertTrue("Per-card palettes should vary across app cards", chosen.size > 1)
        assertEquals(RgbNeonPalette.paletteFor("same_seed"), RgbNeonPalette.paletteFor("same_seed"))

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(RgbNeonPalette.isReducedMotionEnabled(context))
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertFalse(RgbNeonPalette.isReducedMotionEnabled(context))
    }

    @Test
    fun homeScreen_rendersHasimShowcaseNonInteractivelyWithoutSubtitleOrSoundToggle() {
        val apps = sampleApps()
        var searchOpened = false
        var selectedApp: ApplicationItem? = null

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                HomeScreen(
                    applications = apps,
                    userProfile = null,
                    onOpenSearch = { searchOpened = true },
                    onSelectApp = { selectedApp = it },
                    onOpenProfile = {}
                )
            }
        }
        composeRule.waitForIdle()

        // HASIM APK / HASIM branding present
        composeRule.onNodeWithTag("featured_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("showcase_heading", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("HASIM APK")
        composeRule.onNodeWithTag("showcase_brand_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("HASIM")

        // Removed elements must NOT exist
        composeRule.onNodeWithText("HASHIM").assertDoesNotExist()
        composeRule.onNodeWithText("HASHIM APK").assertDoesNotExist()
        composeRule.onNodeWithText("FEATURED SPOTLIGHT").assertDoesNotExist()
        composeRule.onNodeWithText("STORE PRO • 3D TRENDING SHOWCASE").assertDoesNotExist()
        composeRule.onNodeWithTag("showcase_sound_toggle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Enable showcase sound").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Mute showcase sound").assertDoesNotExist()

        // VN Video Editor must NOT appear in featured_banner, while staying intact in catalog rows
        composeRule.onNode(
            androidx.compose.ui.test.hasText("VN - Video Editor") and
                hasAnyAncestor(hasTestTag("featured_banner"))
        ).assertDoesNotExist()

        // 5 3D orbital app icons remain visible & strictly non-interactive
        composeRule.onNodeWithTag("showcase_3d_carousel", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("showcase_3d_item_chatgpt").assertExists()
        composeRule.onNodeWithTag("showcase_3d_item_youtube").assertExists()
        composeRule.onNodeWithTag("showcase_3d_item_instagram").assertExists()
        composeRule.onNodeWithTag("showcase_3d_item_whatsapp").assertExists()
        composeRule.onNodeWithTag("showcase_3d_item_facebook").assertExists()

        composeRule.onNodeWithTag("featured_banner").assert(hasClickAction().not())
        composeRule.onNodeWithTag("showcase_3d_item_chatgpt").assert(hasClickAction().not())
        composeRule.onNodeWithTag("featured_banner").performClick()
        composeRule.onNodeWithTag("showcase_3d_item_chatgpt").performClick()
        composeRule.waitForIdle()
        assertFalse("Banner click must not open search", searchOpened)
        assertNull("Banner click must not open app details", selectedApp)

        // VN Video Editor & other marketplace sections remain intact and clickable
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("app_row_app_vn"))
        composeRule.onNodeWithTag("app_row_app_vn").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("app_row_app_notes"))
        composeRule.onNodeWithTag("app_row_app_notes").assertIsDisplayed()
    }

    @Test
    fun searchScreen_rendersPopularAndFilteredAppCardsWithGlow() {
        val apps = sampleApps()
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                SearchScreen(
                    applications = apps,
                    onSelectApp = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Unfocused search box keeps RGB glow inactive; focusing activates Cyan/Green/Blue/Purple glow
        composeRule.onNodeWithTag("search_box_rgb_inactive").assertIsDisplayed()
        composeRule.onNodeWithTag("search_box_rgb_active").assertDoesNotExist()

        composeRule.onNodeWithTag("search_input_field").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_box_rgb_active").assertIsDisplayed()
        composeRule.onNodeWithTag("search_box_rgb_inactive").assertDoesNotExist()

        composeRule.onNodeWithTag("app_row_app_vn").assertIsDisplayed()
        composeRule.onNodeWithTag("app_row_app_vlc").assertIsDisplayed()
    }

    @Test
    fun searchBox_matchesExactWidthAndHeightBetweenHomeAndSearchScreen() {
        val apps = sampleApps()
        val showSearch = androidx.compose.runtime.mutableStateOf(false)

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                if (!showSearch.value) {
                    HomeScreen(
                        applications = apps,
                        userProfile = null,
                        onOpenSearch = { showSearch.value = true },
                        onSelectApp = {},
                        onOpenProfile = {}
                    )
                } else {
                    SearchScreen(
                        applications = apps,
                        onSelectApp = {},
                        onBack = { showSearch.value = false }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val homeBounds = composeRule.onNodeWithTag("home_search_glow_inactive")
            .fetchSemanticsNode()
            .boundsInRoot

        showSearch.value = true
        composeRule.waitForIdle()

        val searchBounds = composeRule.onNodeWithTag("search_box_rgb_inactive")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(homeBounds.width, searchBounds.width, 1.5f)
        assertEquals(homeBounds.height, searchBounds.height, 1.5f)
        composeRule.onNodeWithText("Search apps, developers, keywords...", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun profileScreen_rendersUploadDownloadAndSavedCardsWithGlow() {
        val apps = sampleApps()
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ProfileScreen(
                    isSignedIn = true,
                    isAdminAuthorized = true,
                    isServerAdminVerified = true,
                    userProfile = UserProfile("u1", "hasimsk12345678sk@gmail.com", "Admin"),
                    myUploads = listOf(apps.first()),
                    downloads = listOf(
                        DownloadItem(
                            downloadId = "dl_1",
                            appId = "app_vlc",
                            appName = "VLC Media Player",
                            version = "3.5.4"
                        )
                    ),
                    savedApps = listOf(
                        SavedAppItem(
                            appId = "app_nova",
                            appName = "Nova Launcher",
                            developerName = "Nova Studio",
                            version = "8.0.5"
                        )
                    ),
                    onSelectApp = {},
                    onEditApp = {},
                    onDeleteApp = {},
                    onOpenUpload = {},
                    onGoogleSignIn = {},
                    onAdminLogout = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("upload_app_card_app_vn"))
        composeRule.onNodeWithTag("upload_app_card_app_vn").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_tab_downloads").performClick()
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("download_app_card_app_vlc"))
        composeRule.onNodeWithTag("download_app_card_app_vlc").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_tab_saved_apps").performClick()
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("saved_app_card_app_nova"))
        composeRule.onNodeWithTag("saved_app_card_app_nova").assertIsDisplayed()
    }
}
