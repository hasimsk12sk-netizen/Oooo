package com.example

import android.content.Context
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadController
import com.example.data.local.SearchQueryEntity
import com.example.data.local.StoreProDatabase
import com.example.data.repository.StoreProRepository
import com.example.ui.admin.AdminAuthManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_launchesDirectlyWithoutCrashing_andHasCustomLauncherIcons() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(FirebaseApp.getInstance())
        assertNotEquals(0, context.applicationInfo.icon)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))

        composeRule.onNodeWithText("STORE PRO").assertIsDisplayed()
        composeRule.onNodeWithText("Discover").assertIsDisplayed()

        // Ensure online update blockers, dialogs, and buttons are permanently gone
        composeRule.onNodeWithTag("mandatory_update_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("app_update_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("UPDATE NOW", ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("Mandatory Update Required", ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("Update Failed", ignoreCase = true).assertDoesNotExist()
        assertFalse(File(context.filesDir, "app_updates").exists())
    }

    @Test
    fun mainActivity_navigatesAcrossHomeSearchProfileAndAdminLoginWithoutCrashing() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // Open Search screen (initializes Room StoreProDatabase)
        composeRule.onNodeWithTag("home_search_bar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_input_field").assertIsDisplayed()

        // Return Home
        composeRule.onNodeWithTag("search_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("STORE PRO").assertIsDisplayed()

        // Open Upload FAB -> should open Admin Login screen when unauthenticated
        composeRule.onNodeWithTag("upload_fab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Admin Upload Studio").assertIsDisplayed()

        // Return Home
        composeRule.onNodeWithTag("admin_login_back_button").performClick()
        composeRule.waitForIdle()

        // Open Profile / My Library tab
        composeRule.onNodeWithTag("home_profile_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Guest Visitor").assertIsDisplayed()
    }

    @Test
    fun roomSearchHistory_persistsLastFiveQueries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = StoreProDatabase.getInstance(context).searchHistoryDao()
        dao.clearHistory()
        for (i in 1..6) {
            dao.insertQuery(SearchQueryEntity("query_$i", timestamp = i.toLong() * 1000L))
        }
        val recent = dao.getRecentQueries().first()
        assertTrue(recent.size == 5)
        assertTrue(recent.first() == "query_6")
        dao.clearHistory()
    }

    @Test
    fun adminSession_persistsForAuthorizedEmailOnly_andClearsOnLogout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AdminAuthManager.logout(context)
        assertFalse(AdminAuthManager.isAdminLoggedIn(context))

        assertFalse(AdminAuthManager.authenticate(context, "@hasim12", "Hasim#111"))
        assertFalse(AdminAuthManager.isAdminLoggedIn(context))

        assertTrue(AdminAuthManager.authenticate(context, "hasimsk12345678sk@gmail.com", "Hasim#111"))
        assertTrue(AdminAuthManager.isAdminLoggedIn(context))
        assertTrue(AdminAuthManager.getAdminUsername(context) == "hasimsk12345678sk@gmail.com")

        AdminAuthManager.logout(context)
        assertFalse(AdminAuthManager.isAdminLoggedIn(context))
    }

    @Test
    fun downloadController_preventsDuplicateBrowserCountWithinCooldown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = DownloadController(context, StoreProRepository(context))

        assertTrue(controller.tryAcquireBrowserDownloadSlot("app_1", nowMs = 10_000L))
        // Rapid duplicate tap 500ms later is blocked
        assertFalse(controller.tryAcquireBrowserDownloadSlot("app_1", nowMs = 10_500L))
        // Separate app can still download immediately
        assertTrue(controller.tryAcquireBrowserDownloadSlot("app_2", nowMs = 10_500L))
        // After cooldown elapses, new distinct download action succeeds
        assertTrue(controller.tryAcquireBrowserDownloadSlot("app_1", nowMs = 13_000L))
        // On error release, immediate retry succeeds
        controller.releaseBrowserDownloadSlot("app_1")
        assertTrue(controller.tryAcquireBrowserDownloadSlot("app_1", nowMs = 13_100L))
    }

    @Test
    fun uploadAppScreen_showsRestoredTwoTabStudioWithPublishAppAndPublishUpdate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = StoreProRepository(context)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                com.example.ui.theme.MyApplicationTheme(darkTheme = true) {
                    com.example.ui.upload.UploadAppScreen(
                        currentUserId = "admin_uid",
                        repository = repo,
                        onBack = {},
                        onUploadSuccess = {},
                        onAdminLogout = {},
                        onUnauthorized = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Admin Upload Studio").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_tab_publish_app").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_tab_publish_update").assertIsDisplayed()
        composeRule.onNodeWithTag("input_app_name").assertIsDisplayed()
        composeRule.onNodeWithTag("submit_upload_button").performScrollTo().assertIsDisplayed()

        // Switching to Publish Update opens the rebuilt Admin-only Update form + History button
        composeRule.onNodeWithTag("studio_tab_publish_update").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_open_history_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_live_version_code_input").performScrollTo().assertIsDisplayed()
    }
}
