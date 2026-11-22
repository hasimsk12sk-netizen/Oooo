package com.example

import com.example.data.download.FileSharingLinkResolver
import com.example.ui.admin.AdminAuthManager
import com.example.ui.upload.UniversalImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreProFeaturesTest {

    @Test
    fun testUniversalImageValidation() {
        assertTrue(UniversalImageUtils.isValidImageUrl("https://example.com/icon.png"))
        assertTrue(UniversalImageUtils.isValidImageUrl("http://example.com/icon.jpg"))
        assertTrue(UniversalImageUtils.isValidImageUrl("http://cdn.site.org/banner.gif"))
        assertTrue(UniversalImageUtils.isValidImageUrl("https://cdn.site.org/icon.webp?v=2"))
        assertTrue(UniversalImageUtils.isValidImageUrl("https://drive.google.com/file/d/12345/view"))
        assertTrue(UniversalImageUtils.isValidImageUrl("https://images.unsplash.com/photo-123456?w=400"))
        assertFalse(UniversalImageUtils.isValidImageUrl("ftp://invalid.com/file.png"))
        assertFalse(UniversalImageUtils.isValidImageUrl("https://invalid host with spaces.com/a.png"))
        assertFalse(UniversalImageUtils.isValidImageUrl("not-a-url"))
    }

    @Test
    fun testUniversalImageNormalization() {
        assertEquals(
            "https://drive.google.com/thumbnail?id=abc123XYZ&sz=w1000",
            UniversalImageUtils.normalizeImageUrl("https://drive.google.com/file/d/abc123XYZ/view?usp=sharing")
        )
        assertEquals(
            "https://www.dropbox.com/s/xyz/icon.png?raw=1",
            UniversalImageUtils.normalizeImageUrl("https://www.dropbox.com/s/xyz/icon.png?dl=0")
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/main/icon.png",
            UniversalImageUtils.normalizeImageUrl("https://github.com/user/repo/blob/main/icon.png")
        )
        assertEquals(
            "https://i.imgur.com/AbCd123.png",
            UniversalImageUtils.normalizeImageUrl("https://imgur.com/AbCd123")
        )
    }

    @Test
    fun testMultipleScreenshotsParsing() {
        val raw = """
            https://example.com/shot1.png, https://example.com/shot2.jpg
            http://example.com/shot3.webp
            https://example.com/shot1.png
        """.trimIndent()
        val parsed = UniversalImageUtils.parseMultipleImageUrls(raw)
        assertEquals(3, parsed.size)
        assertEquals("https://example.com/shot1.png", parsed[0])
        assertEquals("https://example.com/shot2.jpg", parsed[1])
        assertEquals("http://example.com/shot3.webp", parsed[2])
    }

    @Test
    fun testFileSharingLinkIdentification() {
        assertEquals("Google Drive", FileSharingLinkResolver.identifyService("https://drive.google.com/file/d/123/view"))
        assertEquals("MediaFire", FileSharingLinkResolver.identifyService("https://www.mediafire.com/file/xyz/app.apk/file"))
        assertEquals("Dropbox", FileSharingLinkResolver.identifyService("https://www.dropbox.com/s/123/app.apk?dl=0"))
        assertEquals("GitHub Releases", FileSharingLinkResolver.identifyService("https://github.com/org/repo/releases/download/v1.0/app.apk"))
    }

    @Test
    fun testAdminCredentialVerification() {
        assertTrue(AdminAuthManager.verifyCredentials("hasimsk12345678sk@gmail.com", "Hasim#111"))
        assertTrue(AdminAuthManager.verifyCredentials("  Hasimsk12345678sk@gmail.com ", "Hasim#111"))
        assertFalse(AdminAuthManager.verifyCredentials("@hasim12", "Hasim#111"))
        assertFalse(AdminAuthManager.verifyCredentials("hasimsk12345678sk@gmail.com", "wrongpass"))
        assertFalse(AdminAuthManager.verifyCredentials("otheradmin@gmail.com", "Hasim#111"))
        assertFalse(AdminAuthManager.verifyCredentials("", ""))
    }

    @Test
    fun testFirestoreTimestampParsing() {
        val expectedMillis = 1_732_000_000_123L
        val ts = com.google.firebase.Timestamp(expectedMillis / 1000, ((expectedMillis % 1000) * 1_000_000).toInt())
        assertEquals(expectedMillis, com.example.data.repository.StoreProRepository.parseEpochMillis(ts, 0L))
        assertEquals(expectedMillis, com.example.data.repository.StoreProRepository.parseEpochMillis(java.util.Date(expectedMillis), 0L))
        assertEquals(expectedMillis, com.example.data.repository.StoreProRepository.parseEpochMillis(expectedMillis, 0L))
        assertEquals(12345L, com.example.data.repository.StoreProRepository.parseEpochMillis(12345, 0L))
        assertEquals(expectedMillis, com.example.data.repository.StoreProRepository.parseEpochMillis(expectedMillis.toString(), 0L))
        assertEquals(999L, com.example.data.repository.StoreProRepository.parseEpochMillis(null, 999L))
        assertEquals(999L, com.example.data.repository.StoreProRepository.parseEpochMillis("invalid", 999L))
    }
}
