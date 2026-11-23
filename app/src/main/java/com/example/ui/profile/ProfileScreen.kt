package com.example.ui.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import coil.size.Precision
import com.example.ui.components.rememberLifecycleIsResumed
import com.example.util.DisplayRefreshRateOptimizer
import com.example.util.StoreProShareHelper
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ApplicationItem
import com.example.data.model.DownloadItem
import com.example.data.model.RegisteredDevice
import com.example.data.model.SavedAppItem
import com.example.data.model.UserProfile
import com.example.ui.admin.AdminAuthManager
import com.example.ui.components.RgbNeonPalette
import com.example.ui.components.rgbNeonGlowBorder
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.upload.UniversalImageLoader
import com.example.ui.upload.UniversalImageUtils
import com.example.util.SocialLinkLauncher
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    isSignedIn: Boolean,
    isAdminAuthorized: Boolean,
    userProfile: UserProfile?,
    myUploads: List<ApplicationItem>,
    downloads: List<DownloadItem>,
    savedApps: List<SavedAppItem>,
    onSelectApp: (ApplicationItem) -> Unit,
    onEditApp: (ApplicationItem) -> Unit,
    onDeleteApp: (String) -> Unit,
    onOpenUpload: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onAdminLogout: () -> Unit,
    onSignOut: () -> Unit,
    isServerAdminVerified: Boolean = isAdminAuthorized,
    registeredDevices: List<RegisteredDevice> = emptyList(),
    notificationsEnabled: Boolean = true,
    onToggleDeviceBlock: (RegisteredDevice) -> Unit = {},
    onDeleteDevice: (String) -> Unit = {},
    isRefreshingDevices: Boolean = false,
    lastDeviceSyncAtMs: Long = 0L,
    onRefreshDevices: () -> Unit = {},
    onCleanupStaleDevices: () -> Unit = {},
    onEnableNotifications: () -> Unit = {},
    onWhatsAppClick: (() -> Unit)? = null,
    onTelegramClick: (() -> Unit)? = null,
    onShareStoreProClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isResumed = rememberLifecycleIsResumed()
    val reducedMotion = remember(context) { RgbNeonPalette.isReducedMotionEnabled(context) }
    val powerSave = remember(context, isResumed) { DisplayRefreshRateOptimizer.isPowerSaveMode(context) }
    val animateSocial = isResumed && !reducedMotion && !powerSave
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val avatarSizePx = remember(density) { with(density) { 80.dp.toPx().roundToInt().coerceAtLeast(160) } }
    val canAccessAdminDeviceTab = isAdminAuthorized && isServerAdminVerified
    val tabs = remember(canAccessAdminDeviceTab) {
        if (canAccessAdminDeviceTab) {
            listOf("My Uploads", "Downloads", "Saved Apps", "Device")
        } else {
            listOf("My Uploads", "Downloads", "Saved Apps")
        }
    }
    var selectedTab by remember { mutableIntStateOf(if (isAdminAuthorized) 0 else 1) }
    var deviceSearchQuery by remember { mutableStateOf("") }
    var expandedDeviceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var devicePendingDelete by remember { mutableStateOf<RegisteredDevice?>(null) }
    var deviceNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val normalizedAvatar = remember(userProfile?.avatarUrl) {
        userProfile?.avatarUrl?.takeIf { it.isNotBlank() }?.let { UniversalImageUtils.normalizeImageUrl(it) }
    }
    val avatarRequest = remember(context, normalizedAvatar, avatarSizePx) {
        normalizedAvatar?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(avatarSizePx, avatarSizePx)
                .precision(Precision.INEXACT)
                .crossfade(150)
                .build()
        }
    }

    val preparedDevices = remember(registeredDevices, canAccessAdminDeviceTab, selectedTab) {
        if (canAccessAdminDeviceTab && selectedTab == 3) {
            prepareGenuineDevices(registeredDevices)
        } else {
            emptyList()
        }
    }
    val filteredDevices = remember(preparedDevices, deviceSearchQuery) {
        filterGenuineDevices(preparedDevices, deviceSearchQuery)
    }

    LaunchedEffect(tabs.size) {
        if (selectedTab >= tabs.size) {
            selectedTab = 1
        }
    }

    // Periodic clock tick keeps Online/Offline metrics & relative timestamps auto-updating only when resumed on Device tab
    LaunchedEffect(selectedTab, canAccessAdminDeviceTab, isResumed, lastDeviceSyncAtMs) {
        deviceNowMs = System.currentTimeMillis()
        if (canAccessAdminDeviceTab && selectedTab == 3 && isResumed) {
            while (true) {
                delay(10_000L)
                deviceNowMs = System.currentTimeMillis()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .testTag("profile_lazy_column"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // User / Guest Header Card with Centered Avatar & WhatsApp (Left) + Telegram (Right) + Share STORE PRO (Top-Right)
        item(key = "profile_header_card", contentType = "profile_header") {
            val shareInteractionSource = remember { MutableInteractionSource() }
            val isSharePressed by shareInteractionSource.collectIsPressedAsState()
            val shareScale by animateFloatAsState(
                targetValue = if (isSharePressed) 0.91f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "profile_share_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .rgbNeonGlowBorder(
                        seed = "profile_header_card",
                        cornerRadius = 22.dp,
                        borderWidth = 1.6.dp,
                        glowIntensity = 0.88f
                    )
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Top-Right Corner: Share STORE PRO Button positioned near/above Telegram without disturbing existing layout
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp)
                                    .scale(shareScale)
                                    .rgbNeonGlowBorder(
                                        seed = "profile_share_storepro_btn",
                                        cornerRadius = 15.dp,
                                        borderWidth = 1.5.dp,
                                        glowIntensity = 0.95f
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    StoreProSecondaryCyan.copy(alpha = 0.18f),
                                                    StoreProPrimaryGreen.copy(alpha = 0.18f)
                                                )
                                            )
                                        )
                                        .clickable(
                                            interactionSource = shareInteractionSource,
                                            indication = androidx.compose.material3.ripple(color = StoreProSecondaryCyan)
                                        ) {
                                            if (onShareStoreProClick != null) {
                                                onShareStoreProClick()
                                            } else {
                                                StoreProShareHelper.shareStoreProApp(context)
                                            }
                                        }
                                        .semantics {
                                            contentDescription = "Share STORE PRO"
                                            role = Role.Button
                                        }
                                        .testTag("profile_share_storepro_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share STORE PRO",
                                        tint = StoreProSecondaryCyan,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        val socialTransition = if (animateSocial) {
                            rememberInfiniteTransition(label = "social_icon_motion")
                        } else {
                            null
                        }
                        val waFloatYState = socialTransition?.animateFloat(
                            initialValue = -3.5f,
                            targetValue = 3.5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "wa_float_y"
                        )
                        val waDriftXState = socialTransition?.animateFloat(
                            initialValue = -2f,
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "wa_drift_x"
                        )
                        val waScaleState = socialTransition?.animateFloat(
                            initialValue = 0.97f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "wa_pulse"
                        )

                        val tgGlideXState = socialTransition?.animateFloat(
                            initialValue = 2.5f,
                            targetValue = -2.5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "tg_glide_x"
                        )
                        val tgFloatYState = socialTransition?.animateFloat(
                            initialValue = 3.5f,
                            targetValue = -3.5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "tg_float_y"
                        )
                        val tgTiltState = socialTransition?.animateFloat(
                            initialValue = -3.5f,
                            targetValue = 3.5f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "tg_tilt"
                        )

                        // Left: Official WhatsApp Community Button (graphicsLayer reads prevent header recomposition)
                        SocialBrandIconButton(
                            contentDescription = "Join WhatsApp group",
                            backgroundColor = Color(0xFF25D366).copy(alpha = 0.14f),
                            borderColor = Color(0xFF25D366).copy(alpha = 0.55f),
                            onClick = {
                                if (onWhatsAppClick != null) {
                                    onWhatsAppClick()
                                } else {
                                    SocialLinkLauncher.openWhatsAppCommunity(context)
                                }
                            },
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = waDriftXState?.value ?: 0f
                                    translationY = waFloatYState?.value ?: 0f
                                    val s = waScaleState?.value ?: 1f
                                    scaleX = s
                                    scaleY = s
                                }
                                .testTag("profile_whatsapp_button")
                        ) {
                            WhatsAppBrandGlyph(modifier = Modifier.size(28.dp))
                        }

                        // Center: Existing Profile Avatar (remains cleanly stationary)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(
                                    2.dp,
                                    if (isAdminAuthorized) StoreProPrimaryGreen else StoreProSecondaryCyan,
                                    CircleShape
                                )
                                .testTag("profile_avatar_box"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarRequest != null) {
                                AsyncImage(
                                    model = avatarRequest,
                                    imageLoader = imageLoader,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = StoreProSecondaryCyan,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        // Right: Official Telegram Channel Button (graphicsLayer reads prevent header recomposition)
                        SocialBrandIconButton(
                            contentDescription = "Open Telegram channel",
                            backgroundColor = Color(0xFF229ED9).copy(alpha = 0.14f),
                            borderColor = Color(0xFF229ED9).copy(alpha = 0.55f),
                            onClick = {
                                if (onTelegramClick != null) {
                                    onTelegramClick()
                                } else {
                                    SocialLinkLauncher.openTelegramChannel(context)
                                }
                            },
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = tgGlideXState?.value ?: 0f
                                    translationY = tgFloatYState?.value ?: 0f
                                    rotationZ = tgTiltState?.value ?: 0f
                                }
                                .testTag("profile_telegram_button")
                        ) {
                            TelegramBrandGlyph(modifier = Modifier.size(28.dp))
                        }
                    }
                        }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isSignedIn) {
                            userProfile?.displayName ?: "STORE PRO User"
                        } else {
                            "Guest Visitor"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = if (isSignedIn) {
                            userProfile?.email ?: ""
                        } else {
                            "Browsing without an account • Sign in to sync bookmarks & reviews"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isAdminAuthorized) {
                                    StoreProPrimaryGreen.copy(alpha = 0.15f)
                                } else {
                                    Color(0xFF1E293B)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdminAuthorized) Icons.Default.AdminPanelSettings else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isAdminAuthorized) StoreProPrimaryGreen else StoreProSecondaryCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isAdminAuthorized -> "Authorized Admin (${AdminAuthManager.getAdminUsername(context)})"
                                isSignedIn -> "Signed-In User"
                                else -> "Public Marketplace Access"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAdminAuthorized) StoreProPrimaryGreen else StoreProSecondaryCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .rgbNeonGlowBorder(
                                    seed = "profile_upload_btn",
                                    cornerRadius = 12.dp,
                                    borderWidth = 1.4.dp,
                                    glowIntensity = 0.85f
                                )
                        ) {
                            Button(
                                onClick = onOpenUpload,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StoreProPrimaryGreen,
                                    contentColor = Color(0xFF003816)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("profile_upload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isSignedIn) {
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_signout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onGoogleSignIn,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreProSecondaryCyan),
                                border = BorderStroke(1.dp, StoreProSecondaryCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_google_signin_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Sign-In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isAdminAuthorized) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onAdminLogout,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_admin_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Logout Admin Session", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                }
            }
        }

        if (!notificationsEnabled) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StoreProCardBorder)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = StoreProSecondaryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New App Release Alerts",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Enable notifications to hear about newly published apps.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onEnableNotifications,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StoreProSecondaryCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreProSecondaryCyan),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("profile_enable_notifications_button")
                        ) {
                            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Tabs Row
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                containerColor = Color(0xFF0A0F1D),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.coerceIn(0, tabs.lastIndex)]),
                        color = StoreProPrimaryGreen
                    )
                },
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.testTag("profile_tab_${title.lowercase().replace(' ', '_')}"),
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) StoreProPrimaryGreen else Color(0xFF94A3B8)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                if (!isAdminAuthorized) {
                    item {
                        EmptyStateCard(
                            title = "Administrator Access Required",
                            subtitle = "Only authorized administrators can upload, edit, or delete marketplace applications."
                        )
                    }
                } else if (myUploads.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No applications published yet",
                            subtitle = "Tap Upload App above to publish your first application."
                        )
                    }
                } else {
                    items(myUploads, key = { "upload_${it.appId}" }, contentType = { "upload_card" }) { app ->
                        MyUploadItemCard(
                            app = app,
                            onEdit = { onEditApp(app) },
                            onDelete = { onDeleteApp(app.appId) },
                            onClick = { onSelectApp(app) }
                        )
                    }
                }
            }

            1 -> {
                if (!isSignedIn) {
                    item(key = "downloads_guest_empty", contentType = "empty_card") {
                        EmptyStateCard(
                            title = "Personal Download History",
                            subtitle = "Regular users can download apps without logging in. Sign in with Google if you want to sync download history across devices."
                        )
                    }
                } else if (downloads.isEmpty()) {
                    item(key = "downloads_empty", contentType = "empty_card") {
                        EmptyStateCard(
                            title = "No downloads yet",
                            subtitle = "Browse the marketplace and download applications directly online."
                        )
                    }
                } else {
                    items(downloads, key = { "dl_${it.downloadId}_${it.appId}" }, contentType = { "download_card" }) { dl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .rgbNeonGlowBorder(
                                    seed = "download_${dl.appId.ifBlank { dl.downloadId }}",
                                    cornerRadius = 14.dp,
                                    borderWidth = 1.5.dp,
                                    glowIntensity = 0.9f
                                )
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_app_card_${dl.appId.ifBlank { dl.downloadId }}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = StoreProSecondaryCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dl.appName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Version ${dl.version} • Status: Completed",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (!isSignedIn) {
                    item(key = "saved_guest_empty", contentType = "empty_card") {
                        EmptyStateCard(
                            title = "Saved Bookmarks",
                            subtitle = "Sign in with Google to bookmark applications and access them across devices."
                        )
                    }
                } else if (savedApps.isEmpty()) {
                    item(key = "saved_empty", contentType = "empty_card") {
                        EmptyStateCard(
                            title = "No saved applications",
                            subtitle = "Bookmark apps to access them quickly anytime."
                        )
                    }
                } else {
                    items(savedApps, key = { "saved_${it.appId}" }, contentType = { "saved_card" }) { saved ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .rgbNeonGlowBorder(
                                    seed = "saved_${saved.appId}",
                                    cornerRadius = 14.dp,
                                    borderWidth = 1.5.dp,
                                    glowIntensity = 0.9f
                                )
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_app_card_${saved.appId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = StoreProPrimaryGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.appName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${saved.developerName} • v${saved.version}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                if (canAccessAdminDeviceTab) {
                    deviceManagementTabItems(
                        genuineDevices = preparedDevices,
                        filteredDevices = filteredDevices,
                        nowMs = deviceNowMs,
                        searchQuery = deviceSearchQuery,
                        onSearchQueryChange = { deviceSearchQuery = it },
                        expandedDeviceIds = expandedDeviceIds,
                        onToggleExpandDevice = { id ->
                            expandedDeviceIds = if (expandedDeviceIds.contains(id)) {
                                expandedDeviceIds - id
                            } else {
                                expandedDeviceIds + id
                            }
                        },
                        onToggleBlock = onToggleDeviceBlock,
                        onRequestDeleteDevice = { target -> devicePendingDelete = target },
                        isRefreshing = isRefreshingDevices,
                        lastSyncAtMs = lastDeviceSyncAtMs,
                        onRefreshDevices = onRefreshDevices,
                        onCleanupStaleDevices = onCleanupStaleDevices
                    )
                } else {
                    item {
                        EmptyStateCard(
                            title = "Administrator Access Required",
                            subtitle = "Device management is restricted to verified administrators."
                        )
                    }
                }
            }
        }
    }

    val pendingDelete = devicePendingDelete
    if (pendingDelete != null) {
        DeviceDeleteConfirmationDialog(
            device = pendingDelete,
            onDismiss = { devicePendingDelete = null },
            onConfirmDelete = {
                devicePendingDelete = null
                onDeleteDevice(pendingDelete.installationId)
            }
        )
    }
}

@Composable
private fun SocialBrandIconButton(
    contentDescription: String,
    backgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun WhatsAppBrandGlyph(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bubbleBrush = Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
            val radius = w * 0.44f
            val center = Offset(w * 0.50f, h * 0.48f)
            val tail = Path().apply {
                moveTo(w * 0.22f, h * 0.74f)
                lineTo(w * 0.11f, h * 0.92f)
                lineTo(w * 0.36f, h * 0.85f)
                close()
            }
            val handset = Path().apply {
                moveTo(w * 0.35f, h * 0.33f)
                quadraticTo(w * 0.35f, h * 0.63f, w * 0.66f, h * 0.64f)
            }
            val handsetStroke = Stroke(
                width = w * 0.13f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            onDrawBehind {
                drawCircle(brush = bubbleBrush, radius = radius, center = center)
                drawPath(path = tail, color = Color(0xFF128C7E))
                drawPath(path = handset, color = Color.White, style = handsetStroke)
            }
        }
    )
}

@Composable
private fun TelegramBrandGlyph(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bgBrush = Brush.linearGradient(listOf(Color(0xFF34B7F1), Color(0xFF0088CC)))
            val radius = w * 0.45f
            val center = Offset(w * 0.50f, h * 0.50f)
            val paperPlane = Path().apply {
                moveTo(w * 0.21f, h * 0.50f)
                lineTo(w * 0.76f, h * 0.28f)
                lineTo(w * 0.65f, h * 0.75f)
                lineTo(w * 0.49f, h * 0.61f)
                lineTo(w * 0.41f, h * 0.70f)
                lineTo(w * 0.40f, h * 0.56f)
                close()
            }
            val lineStart = Offset(w * 0.40f, h * 0.56f)
            val lineEnd = Offset(w * 0.69f, h * 0.35f)
            val lineWidth = w * 0.055f
            onDrawBehind {
                drawCircle(brush = bgBrush, radius = radius, center = center)
                drawPath(path = paperPlane, color = Color.White)
                drawLine(
                    color = Color(0xFF0088CC),
                    start = lineStart,
                    end = lineEnd,
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    )
}

@Composable
private fun MyUploadItemCard(
    app: ApplicationItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { UniversalImageLoader.get(context) }
    val iconSizePx = remember(density) { with(density) { 48.dp.toPx().roundToInt().coerceAtLeast(96) } }
    val normalizedIconUrl = remember(app.iconUrl) { UniversalImageUtils.normalizeImageUrl(app.iconUrl) }
    val iconRequest = remember(context, normalizedIconUrl, iconSizePx) {
        ImageRequest.Builder(context)
            .data(normalizedIconUrl)
            .size(iconSizePx, iconSizePx)
            .precision(Precision.INEXACT)
            .crossfade(150)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .rgbNeonGlowBorder(
                seed = "upload_${app.appId}_${app.appName}",
                cornerRadius = 14.dp,
                borderWidth = 1.5.dp,
                glowIntensity = 0.9f
            )
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .testTag("upload_app_card_${app.appId}")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = iconRequest,
                        imageLoader = imageLoader,
                        contentDescription = app.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "v${app.version} • ${app.downloads} dl",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = StoreProSecondaryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}
