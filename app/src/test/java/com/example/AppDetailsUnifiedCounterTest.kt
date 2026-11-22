package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.download.DownloadProgressState
import com.example.data.model.ApplicationItem
import com.example.ui.details.AppDetailsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDetailsUnifiedCounterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothDownloadButtons_areVisible_andDisplayUnifiedTotalCounter() {
        var downloadCount by mutableLongStateOf(42L)
        var browserClicks = 0
        var directClicks = 0

        val baseApp = ApplicationItem(
            appId = "app_unified_test",
            appName = "STORE PRO Utility",
            developerName = "Studio Dev",
            version = "1.2.0",
            description = "Unified download counter test app",
            iconUrl = "https://example.com/icon.png",
            apkUrl = "https://example.com/app.apk",
            fileSizeBytes = 10 * 1024 * 1024L,
            downloads = 42L
        )

        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AppDetailsScreen(
                    app = baseApp.copy(downloads = downloadCount),
                    reviews = emptyList(),
                    isSaved = false,
                    currentUserId = null,
                    currentUserName = "Guest",
                    downloadProgress = DownloadProgressState(),
                    onBack = {},
                    onToggleSave = {},
                    onRequireSignIn = {},
                    onStartDownload = {
                        directClicks++
                        downloadCount += 1L
                    },
                    onCancelDownload = {},
                    onInstallApk = {},
                    onOpenBrowserDownload = {
                        browserClicks++
                        downloadCount += 1L
                    },
                    onSubmitReview = { _, _, _ -> },
                    onDeleteReview = {}
                )
            }
        }

        composeRule.onNodeWithTag("details_downloads_count").assertTextEquals("42")
        composeRule.onNodeWithTag("download_apk_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, browserClicks)
        composeRule.onNodeWithTag("details_downloads_count").assertTextEquals("43")

        composeRule.onNodeWithTag("download_in_app_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, directClicks)
        composeRule.onNodeWithTag("details_downloads_count").assertTextEquals("44")
    }
}
