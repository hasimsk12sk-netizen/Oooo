package com.example.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApplicationItem
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.upload.AppIconPreviewBox
import com.example.ui.upload.ScreenshotsPreviewGallery
import com.example.ui.upload.UniversalImageUtils

@Composable
fun EditAppDialog(
    app: ApplicationItem,
    onDismiss: () -> Unit,
    onSave: (ApplicationItem) -> Unit
) {
    var appName by remember { mutableStateOf(app.appName) }
    var version by remember { mutableStateOf(app.version) }
    var iconUrl by remember { mutableStateOf(app.iconUrl) }
    var apkUrl by remember { mutableStateOf(app.apkUrl) }
    var screenshotsText by remember { mutableStateOf(app.screenshots.joinToString("\n")) }
    var description by remember { mutableStateOf(app.description) }
    var whatsNew by remember { mutableStateOf(app.whatsNew) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131B2E),
        title = {
            Text(
                text = "Edit Application Details",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_app_name")
                )

                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Version", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_version")
                )

                OutlinedTextField(
                    value = iconUrl,
                    onValueChange = { iconUrl = it },
                    label = { Text("Application Icon URL", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("HTTP/HTTPS image URL (JPG, PNG, WEBP, GIF)", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_icon_url")
                )

                AppIconPreviewBox(iconUrl = iconUrl)

                OutlinedTextField(
                    value = screenshotsText,
                    onValueChange = { screenshotsText = it },
                    label = { Text("Screenshot URLs (Optional, comma or line separated)", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("HTTP/HTTPS image URLs separated by commas or newlines", color = Color(0xFF64748B)) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_screenshots")
                )

                ScreenshotsPreviewGallery(rawUrls = screenshotsText)

                OutlinedTextField(
                    value = apkUrl,
                    onValueChange = { apkUrl = it },
                    label = { Text("APK / File-Sharing Download Link", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("Google Drive, Dropbox, MediaFire, GitHub...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_apk_url")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = Color(0xFF94A3B8)) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_description")
                )

                OutlinedTextField(
                    value = whatsNew,
                    onValueChange = { whatsNew = it },
                    label = { Text("What's New in this update", color = Color(0xFF94A3B8)) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = StoreProPrimaryGreen,
                        unfocusedBorderColor = StoreProCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_whats_new")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedScreenshots = UniversalImageUtils
                        .parseMultipleImageUrls(screenshotsText)
                        .take(20)
                        .map { UniversalImageUtils.normalizeImageUrl(it).take(2000) }
                    val updated = app.copy(
                        appName = appName.trim().take(120),
                        version = version.trim().take(40),
                        iconUrl = UniversalImageUtils.normalizeImageUrl(iconUrl.trim()).take(2000),
                        screenshots = normalizedScreenshots,
                        apkUrl = apkUrl.trim().take(2000),
                        description = description.trim().take(5000),
                        whatsNew = whatsNew.trim().take(2000)
                    )
                    onSave(updated)
                },
                enabled = appName.isNotBlank() &&
                    description.isNotBlank() &&
                    version.isNotBlank() &&
                    apkUrl.trim().length >= 8 &&
                    (apkUrl.trim().startsWith("http://", ignoreCase = true) || apkUrl.trim().startsWith("https://", ignoreCase = true)) &&
                    UniversalImageUtils.isValidImageUrl(iconUrl),
                colors = ButtonDefaults.buttonColors(containerColor = StoreProPrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes", color = Color(0xFF003816), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
