package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadProgressState
import com.example.data.model.AppCategory
import com.example.data.model.ApplicationItem
import com.example.data.model.CatalogUiState
import com.example.data.model.UserProfile
import com.example.ui.components.RgbNeonPalette
import com.example.ui.details.AppDetailsScreen
import com.example.ui.home.AllAppsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.StoreProDeepLinkParser
import com.example.util.StoreProDeepLinkTarget
import com.example.util.StoreProShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreProV13UpgradeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun catalogFixture(): List<ApplicationItem> = listOf(
        ApplicationItem(
            appId = "vlc_app",
            appName = "VLC Media Player",
            developerName = "VideoLAN",
            packageId = "org.videolan.vlc",
            version = "3.5.4",
            fileSizeBytes = 25L * 1024L * 1024L,
            downloads = 9800L,
            averageRating = 4.9,
            status = "published",
            description = "Fast open-source video and audio player",
            createdAt = 3000L
        ),
        ApplicationItem(
            appId = "tg_app",
            appName = "Telegram X",
            developerName = "Telegram FZ",
            packageId = "org.thunderdog.challegram",
            version = "0.26.3",
            fileSizeBytes = 18L * 1024L * 1024L,
            downloads = 6400L,
            averageRating = 4.7,
            status = "published",
            description = "Fast cloud messaging and chat channels",
            createdAt = 2000L
        ),
        ApplicationItem(
            appId = "draft_secret",
            appName = "Hidden Draft App",
            developerName = "Internal QA",
            packageId = "com.internal.draft",
            version = "0.9.0",
            fileSizeBytes = 5L * 1024L * 1024L,
            downloads = 10L,
            averageRating = 5.0,
            status = "draft",
            description = "Should not be shown to public users",
            createdAt = 5000L
        )
    )

    @Test
    fun homeScreen_displaysAllAppsSectionWithPublishedAppsOnlyAndWorkingCallbacks() {
        val apps = catalogFixture()
        var downloadedApp: ApplicationItem? = null
        var seeAllClicked = false

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                HomeScreen(
                    applications = apps,
                    userProfile = null,
                    onOpenSearch = {},
                    onSelectApp = {},
                    onOpenProfile = {},
                    catalogState = CatalogUiState.Success(apps.filter { it.status == "published" }),
                    onDownloadApp = { downloadedApp = it },
                    onOpenAllApps = { seeAllClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_all_apps_section"))
        composeRule.onNodeWithTag("home_all_apps_section").assertIsDisplayed()
        composeRule.onNodeWithText("ALL APPS", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_all_apps_see_all_button").performClick()
        assertTrue(seeAllClicked)

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_all_apps_card_vlc_app"))
        composeRule.onNodeWithTag("home_all_apps_card_vlc_app").assertIsDisplayed()
        composeRule.onNodeWithTag("home_all_apps_card_draft_secret").assertDoesNotExist()

        composeRule.onNodeWithTag("home_all_apps_download_vlc_app").performClick()
        assertEquals("vlc_app", downloadedApp?.appId)
    }

    @Test
    fun allAppsScreen_supportsSearchSortingCategoryLoadingErrorAndEmptyStates() {
        val published = catalogFixture().filter { it.status == "published" }
        var retried = false
        var downloadedApp: ApplicationItem? = null

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AllAppsScreen(
                    catalogState = CatalogUiState.Success(published),
                    onSelectApp = {},
                    onDownloadApp = { downloadedApp = it },
                    onRetry = { retried = true },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("all_apps_screen_card_vlc_app").assertIsDisplayed()
        composeRule.onNodeWithTag("all_apps_screen_card_tg_app").assertIsDisplayed()

        // Filter by MEDIA category -> only VLC matches
        composeRule.onNodeWithTag("all_apps_category_${AppCategory.MEDIA.name}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_apps_screen_card_vlc_app").assertIsDisplayed()
        composeRule.onNodeWithTag("all_apps_screen_card_tg_app").assertDoesNotExist()

        // Search keyword producing empty state, then reset filters
        composeRule.onNodeWithTag("all_apps_search_input").performTextInput("zzzz_nonexistent")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_apps_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("all_apps_clear_filters_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_apps_screen_card_tg_app").assertIsDisplayed()

        // Sort & Download click
        composeRule.onNodeWithTag("all_apps_sort_DOWNLOADS").performClick()
        composeRule.onNodeWithTag("all_apps_screen_download_vlc_app").performClick()
        assertEquals("vlc_app", downloadedApp?.appId)
        assertFalse(retried)
    }

    @Test
    fun allAppsScreen_displaysLoadingAndErrorRetryStates() {
        val stateHolder = androidx.compose.runtime.mutableStateOf<CatalogUiState>(CatalogUiState.Loading)
        var retried = false

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AllAppsScreen(
                    catalogState = stateHolder.value,
                    onSelectApp = {},
                    onDownloadApp = {},
                    onRetry = { retried = true },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_apps_loading_indicator").assertIsDisplayed()

        stateHolder.value = CatalogUiState.Error("Firestore unavailable")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_apps_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("all_apps_retry_button").performClick()
        assertTrue(retried)
    }

    @Test
    fun appDetailsAndProfileShareButtonsWorkWhilePreservingSocialButtons() {
        val sample = catalogFixture().first()
        var detailsShareClicked = false
        var storeProShareClicked = false
        var whatsappClicked = false
        var telegramClicked = false
        val showDetails = androidx.compose.runtime.mutableStateOf(true)

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                if (showDetails.value) {
                    AppDetailsScreen(
                        app = sample,
                        reviews = emptyList(),
                        isSaved = false,
                        currentUserId = null,
                        currentUserName = "Guest",
                        downloadProgress = DownloadProgressState(),
                        onBack = {},
                        onToggleSave = {},
                        onRequireSignIn = {},
                        onStartDownload = {},
                        onCancelDownload = {},
                        onInstallApk = {},
                        onOpenBrowserDownload = {},
                        onSubmitReview = { _, _, _ -> },
                        onDeleteReview = {},
                        onShareClick = { detailsShareClicked = true }
                    )
                } else {
                    ProfileScreen(
                        isSignedIn = false,
                        isAdminAuthorized = false,
                        userProfile = UserProfile(displayName = "Guest"),
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
                        onWhatsAppClick = { whatsappClicked = true },
                        onTelegramClick = { telegramClicked = true },
                        onShareStoreProClick = { storeProShareClicked = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("details_share_button").assertIsDisplayed().performClick()
        assertTrue(detailsShareClicked)

        showDetails.value = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_share_storepro_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("profile_whatsapp_button").performClick()
        composeRule.onNodeWithTag("profile_telegram_button").performClick()
        assertTrue(storeProShareClicked)
        assertTrue(whatsappClicked)
        assertTrue(telegramClicked)
    }

    @Test
    fun shareHelper_formatsMessagesAndCreatesChooserIntentsAccurately() {
        val sample = catalogFixture().first()
        val appMsg = StoreProShareHelper.buildApplicationShareMessage(context, sample)
        assertTrue(appMsg.contains("Application Name: VLC Media Player"))
        assertTrue(appMsg.contains("Developer: VideoLAN"))
        assertTrue(appMsg.contains("Version: 3.5.4"))
        assertTrue(appMsg.contains("https://gen-lang-client-0338531403.web.app/app/vlc_app"))

        val appChooser = StoreProShareHelper.createApplicationShareChooserIntent(context, sample)
        assertEquals(Intent.ACTION_CHOOSER, appChooser.action)

        val storeMsg = StoreProShareHelper.buildStoreProShareMessage(context)
        assertTrue(storeMsg.contains("STORE PRO"))
        assertTrue(storeMsg.contains("Discover premium applications in one place."))
        assertTrue(storeMsg.contains("Download STORE PRO:"))
        assertTrue(storeMsg.contains("https://gen-lang-client-0338531403.web.app/install"))
        assertFalse(StoreProShareHelper.hasExplicitStoreProDownloadUrl(context))
    }

    @Test
    fun deepLinkParser_resolvesHttpsAppLinksCustomSchemesAndExtras() {
        val httpsApp = Intent(Intent.ACTION_VIEW, Uri.parse("https://gen-lang-client-0338531403.web.app/app/vlc_app"))
        assertEquals(StoreProDeepLinkTarget.AppDetails("vlc_app"), StoreProDeepLinkParser.parse(httpsApp))

        val customApp = Intent(Intent.ACTION_VIEW, Uri.parse("storepro://app/tg_app"))
        assertEquals(StoreProDeepLinkTarget.AppDetails("tg_app"), StoreProDeepLinkParser.parse(customApp))

        val httpsInstall = Intent(Intent.ACTION_VIEW, Uri.parse("https://gen-lang-client-0338531403.firebaseapp.com/install"))
        assertEquals(StoreProDeepLinkTarget.InstallLanding, StoreProDeepLinkParser.parse(httpsInstall))

        val customInstall = Intent(Intent.ACTION_VIEW, Uri.parse("storepro://install"))
        assertEquals(StoreProDeepLinkTarget.InstallLanding, StoreProDeepLinkParser.parse(customInstall))

        val notifIntent = Intent().putExtra("target_app_id", "vlc_app")
        assertEquals(StoreProDeepLinkTarget.AppDetails("vlc_app"), StoreProDeepLinkParser.parse(notifIntent))

        val apps = catalogFixture()
        assertNotNull(StoreProDeepLinkParser.findMatchingApplication(apps, "vlc_app"))
        assertNotNull(StoreProDeepLinkParser.findMatchingApplication(apps, "vlc-media-player"))
        assertNotNull(StoreProDeepLinkParser.findMatchingApplication(apps, "org.videolan.vlc"))
        assertNull(StoreProDeepLinkParser.findMatchingApplication(apps, "nonexistent_app"))
    }

    @Test
    fun rgbNeonPalettes_formClosedLoopsForSeamFreeSweepGradients() {
        RgbNeonPalette.palettes.forEach { palette ->
            assertTrue("Closed loop required to avoid seam", palette.first() == palette.last())
            assertTrue(palette.size >= 5)
        }
        assertTrue(RgbNeonPalette.searchFocusColors.first() == RgbNeonPalette.searchFocusColors.last())
    }
}
