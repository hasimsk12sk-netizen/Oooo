package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.device.DeviceAuthorizationState
import com.example.data.device.DeviceManager
import com.example.data.download.DownloadController
import com.example.data.liveupdate.LiveUpdateController
import com.example.data.model.ApplicationItem
import com.example.data.model.UserProfile
import com.example.data.repository.DeviceAndUpdateRepository
import com.example.data.repository.LiveUpdateRepository
import com.example.data.repository.StoreProRepository
import com.example.notifications.PushNotificationCoordinator
import com.example.notifications.StoreProNotificationHelper
import com.example.ui.admin.AdminAuthManager
import com.example.ui.admin.AdminLoginScreen
import com.example.ui.auth.AuthManager
import com.example.ui.auth.AuthScreen
import com.example.ui.details.AppDetailsScreen
import com.example.ui.device.AccessBlockedScreen
import com.example.ui.edit.EditAppDialog
import com.example.ui.home.HomeScreen
import com.example.ui.liveupdate.AdminLiveUpdateScreen
import com.example.ui.liveupdate.LiveUpdateDashboardScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.search.SearchScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StoreProCardBackground
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan
import com.example.ui.upload.UploadAppScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StoreNavScreen {
    HOME,
    SEARCH,
    DETAILS,
    ADMIN_LOGIN,
    UPLOAD,
    PROFILE,
    AUTH_PROMPT,
    LIVE_UPDATE,
    ADMIN_LIVE_UPDATE
}

class MainActivity : ComponentActivity() {
    private var pendingNotificationIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoreProApplication.ensureFirebaseInitialized(this)
        StoreProNotificationHelper.ensureNotificationChannel(this)
        pendingNotificationIntent = intent
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                val targetAppId = StoreProNotificationHelper.extractTargetAppId(pendingNotificationIntent)
                AppRoot(
                    pendingNotificationAppId = targetAppId,
                    onClearPendingNotification = { pendingNotificationIntent = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent = intent
    }
}

@Composable
fun AppRoot(
    pendingNotificationAppId: String? = null,
    onClearPendingNotification: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    // Realtime Firebase Auth state observation
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Open marketplace Home directly for regular users without mandatory login.
    // Key personal user-scoped streams cleanly by uid or "guest".
    val sessionKey = currentUser?.uid ?: "guest"
    key(sessionKey) {
        MainMarketplaceApp(
            currentUserId = currentUser?.uid,
            currentUser = currentUser,
            credentialManager = credentialManager,
            pendingNotificationAppId = pendingNotificationAppId,
            onClearPendingNotification = onClearPendingNotification,
            onSignOut = {
                AdminAuthManager.logout(context)
                AuthManager.signOut(
                    credentialManager = credentialManager,
                    scope = coroutineScope
                )
            }
        )
    }
}

@Composable
fun MainMarketplaceApp(
    currentUserId: String?,
    currentUser: FirebaseUser?,
    credentialManager: CredentialManager,
    pendingNotificationAppId: String? = null,
    onClearPendingNotification: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val repository = remember(currentUserId) { StoreProRepository(context) }
    val deviceUpdateRepository = remember(currentUserId) { DeviceAndUpdateRepository(context) }
    val liveUpdateRepository = remember(currentUserId) { LiveUpdateRepository(context) }
    val downloadController = remember { DownloadController(context, repository) }
    val deviceManager = remember(deviceUpdateRepository) { DeviceManager(context, deviceUpdateRepository) }
    val liveUpdateController = remember(liveUpdateRepository) { LiveUpdateController(context, liveUpdateRepository) }

    var currentScreen by remember { mutableStateOf(StoreNavScreen.HOME) }
    var previousScreen by remember { mutableStateOf(StoreNavScreen.HOME) }
    var authPromptMessage by remember {
        mutableStateOf("Sign in with Google to save bookmarks, write reviews, and sync your library.")
    }
    var selectedApp by remember { mutableStateOf<ApplicationItem?>(null) }
    var editingApp by remember { mutableStateOf<ApplicationItem?>(null) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var adminSessionVersion by remember { mutableStateOf(0) }
    var isServerAdminVerified by remember(currentUserId, adminSessionVersion) { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(PushNotificationCoordinator.hasOsNotificationPermission(context))
    }
    var showNotificationPromptBanner by remember {
        mutableStateOf(PushNotificationCoordinator.shouldShowInitialPermissionPrompt(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        PushNotificationCoordinator.markPermissionPromptHandled(context)
        showNotificationPromptBanner = false
        hasNotificationPermission = PushNotificationCoordinator.hasOsNotificationPermission(context)
        if (granted) {
            PushNotificationCoordinator.setUserNotificationsOptedOut(context, false)
        }
        PushNotificationCoordinator.syncCurrentFcmToken(context, deviceUpdateRepository, coroutineScope)
    }

    val isAdminAuthorized = remember(currentUser, adminSessionVersion) {
        AdminAuthManager.isFullyAuthorizedAdmin(context, currentUser)
    }
    val canAccessAdminTools = isAdminAuthorized && isServerAdminVerified

    // Realtime Observations from Firestore
    val applications by repository.observeApplications().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedApps by repository.observeSavedApps(currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by repository.observeDownloads(currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadProgress by downloadController.progressState.collectAsStateWithLifecycle()
    val deviceAuthState by deviceManager.authState.collectAsStateWithLifecycle()
    val isRefreshingDeviceStatus by deviceManager.isRefreshingStatus.collectAsStateWithLifecycle()

    val livePublishedReleases by liveUpdateRepository.observePublishedReleases()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val liveUpdateState by liveUpdateController.state.collectAsStateWithLifecycle()

    val adminAllLiveReleases by remember(canAccessAdminTools, liveUpdateRepository) {
        if (canAccessAdminTools) {
            liveUpdateRepository.observeAllReleasesForAdmin()
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val liveRegisteredDevices by remember(canAccessAdminTools, deviceUpdateRepository) {
        if (canAccessAdminTools) {
            deviceUpdateRepository.observeAllRegisteredDevices()
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val selectedAppId = selectedApp?.appId
    val reviews by remember(repository, selectedAppId) {
        if (selectedAppId != null) repository.observeReviews(selectedAppId) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    DisposableEffect(downloadController) {
        onDispose {
            downloadController.cancelDownload()
            liveUpdateController.cancelDownload()
        }
    }

    // Live Device Registration, Heartbeat, and Real-time Block Enforcement
    DisposableEffect(deviceManager) {
        deviceManager.start(coroutineScope)
        onDispose {
            deviceManager.stop()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, deviceManager, liveUpdateController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                deviceManager.checkNow(coroutineScope)
                liveUpdateController.onHostResumed()
                hasNotificationPermission = PushNotificationCoordinator.hasOsNotificationPermission(context)
                PushNotificationCoordinator.syncCurrentFcmToken(context, deviceUpdateRepository, coroutineScope)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep Live Update button badge synced silently with published releases without interrupting startup
    LaunchedEffect(livePublishedReleases) {
        liveUpdateController.applyPublishedReleases(livePublishedReleases)
    }

    // Cleanup any legacy cached self-update APKs, check live updates quietly, & sync FCM token on startup
    LaunchedEffect(deviceUpdateRepository, liveUpdateController) {
        withContext(Dispatchers.IO) {
            runCatching { File(context.filesDir, "app_updates").deleteRecursively() }
        }
        liveUpdateController.refreshUpdates()
        PushNotificationCoordinator.syncCurrentFcmToken(context, deviceUpdateRepository, coroutineScope)
    }

    // Handle deep-link navigation from FCM notification click (foreground, background, or cold launch)
    LaunchedEffect(pendingNotificationAppId, applications) {
        val targetId = pendingNotificationAppId ?: return@LaunchedEffect
        val matched = applications.firstOrNull { it.appId == targetId }
            ?: deviceUpdateRepository.getApplicationById(targetId).getOrNull()
        if (matched != null) {
            selectedApp = matched
            currentScreen = StoreNavScreen.DETAILS
        }
        onClearPendingNotification()
    }

    // Sync signed-in user profile and verify server-side administrator privileges
    LaunchedEffect(currentUserId, currentUser, adminSessionVersion) {
        if (currentUserId != null && currentUser != null) {
            deviceManager.checkNow(coroutineScope, force = true)
            val profileResult = repository.getUserProfileResult(currentUserId)
            val existingProfile = profileResult.getOrNull()
            if (profileResult.isSuccess && existingProfile == null) {
                val initial = UserProfile(
                    userId = currentUserId,
                    email = (currentUser.email ?: "").take(200),
                    displayName = currentUser.displayName?.takeIf { it.isNotBlank() }?.take(100) ?: "STORE PRO User",
                    username = "user_${currentUserId.take(6)}",
                    avatarUrl = currentUser.photoUrl?.toString()?.takeIf { it.length <= 1500 }
                )
                repository.saveUserProfile(initial)
                userProfile = initial
            } else if (existingProfile != null) {
                userProfile = existingProfile
            }

            if (AdminAuthManager.isAuthorizedAdminGoogleUser(currentUser)) {
                repository.seedMarketplaceIfEmpty()
                isServerAdminVerified = if (AdminAuthManager.isFullyAuthorizedAdmin(context, currentUser)) {
                    deviceUpdateRepository.verifyServerAdminAuthorization()
                } else {
                    false
                }
                if (isServerAdminVerified) {
                    deviceUpdateRepository.purgeFakeEmulatorRecordsIfAdmin()
                }
            } else {
                isServerAdminVerified = deviceUpdateRepository.verifyServerAdminAuthorization()
                if (!(isServerAdminVerified && AdminAuthManager.isFullyAuthorizedAdmin(context, currentUser))) {
                    isServerAdminVerified = false
                }
            }
        } else {
            userProfile = null
            isServerAdminVerified = false
        }
    }

    // Keep selectedApp synchronized with live catalog updates
    LaunchedEffect(applications, selectedApp?.appId) {
        val currentId = selectedApp?.appId
        if (currentId != null) {
            applications.firstOrNull { it.appId == currentId }?.let { latest ->
                selectedApp = latest
            }
        }
    }

    // If this installation has been remotely blocked by an administrator, show dedicated AccessBlockedScreen
    val blockedState = deviceAuthState as? DeviceAuthorizationState.Blocked
    if (blockedState != null) {
        AccessBlockedScreen(
            deviceName = blockedState.deviceName,
            deviceModel = blockedState.deviceModel,
            isRefreshing = isRefreshingDeviceStatus,
            onCheckAuthorization = { deviceManager.checkNow(coroutineScope, force = true) }
        )
        return
    }

    val openAdminProtectedUpload: () -> Unit = {
        if (AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
            currentScreen = StoreNavScreen.UPLOAD
        } else {
            previousScreen = currentScreen
            currentScreen = StoreNavScreen.ADMIN_LOGIN
        }
    }

    val openGoogleSignInPrompt: (String) -> Unit = { message ->
        authPromptMessage = message
        previousScreen = currentScreen
        currentScreen = StoreNavScreen.AUTH_PROMPT
    }

    val triggerNotificationPermissionFlow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (PushNotificationCoordinator.shouldShowInitialPermissionPrompt(context)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PushNotificationCoordinator.openAppNotificationSettings(context)
            }
        } else {
            PushNotificationCoordinator.openAppNotificationSettings(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentScreen == StoreNavScreen.HOME || currentScreen == StoreNavScreen.PROFILE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                ) {
                    NavigationBar(
                        containerColor = Color(0xFF131B2E),
                        contentColor = Color.White,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == StoreNavScreen.HOME,
                            onClick = { currentScreen = StoreNavScreen.HOME },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == StoreNavScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Discover", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StoreProPrimaryGreen,
                                selectedTextColor = StoreProPrimaryGreen,
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B),
                                indicatorColor = Color(0xFF1E293B)
                            )
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        NavigationBarItem(
                            selected = currentScreen == StoreNavScreen.PROFILE,
                            onClick = { currentScreen = StoreNavScreen.PROFILE },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == StoreNavScreen.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = "Profile"
                                )
                            },
                            label = { Text("My Library", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StoreProSecondaryCyan,
                                selectedTextColor = StoreProSecondaryCyan,
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B),
                                indicatorColor = Color(0xFF1E293B)
                            )
                        )
                    }

                    // Center Floating Upload Action Button (Admin Protected)
                    FloatingActionButton(
                        onClick = openAdminProtectedUpload,
                        shape = CircleShape,
                        containerColor = StoreProPrimaryGreen,
                        contentColor = Color(0xFF003816),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .size(54.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = 2.dp)
                            .border(3.dp, Color(0xFF0A0F1D), CircleShape)
                            .testTag("upload_fab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Upload Application (Admin)",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showNotificationPromptBanner && currentScreen == StoreNavScreen.HOME) {
                NotificationPermissionBanner(
                    onEnable = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PushNotificationCoordinator.markPermissionPromptHandled(context)
                            showNotificationPromptBanner = false
                        }
                    },
                    onDismiss = {
                        PushNotificationCoordinator.markPermissionPromptHandled(context)
                        showNotificationPromptBanner = false
                    }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreen) {
                    StoreNavScreen.HOME -> {
                        HomeScreen(
                            applications = applications,
                            userProfile = userProfile,
                            onOpenSearch = { currentScreen = StoreNavScreen.SEARCH },
                            onSelectApp = { app ->
                                selectedApp = app
                                currentScreen = StoreNavScreen.DETAILS
                            },
                            onOpenProfile = { currentScreen = StoreNavScreen.PROFILE },
                            liveUpdateState = liveUpdateState,
                            onOpenLiveUpdate = {
                                currentScreen = StoreNavScreen.LIVE_UPDATE
                                coroutineScope.launch { liveUpdateController.refreshUpdates() }
                            }
                        )
                    }

                StoreNavScreen.SEARCH -> {
                    SearchScreen(
                        applications = applications,
                        onSelectApp = { app ->
                            selectedApp = app
                            currentScreen = StoreNavScreen.DETAILS
                        },
                        onBack = { currentScreen = StoreNavScreen.HOME }
                    )
                }

                StoreNavScreen.DETAILS -> {
                    val currentApp = selectedApp?.let { sel ->
                        applications.firstOrNull { it.appId == sel.appId } ?: sel
                    }
                    if (currentApp != null) {
                        val isSaved = savedApps.any { it.appId == currentApp.appId }

                        AppDetailsScreen(
                            app = currentApp,
                            reviews = reviews,
                            isSaved = isSaved,
                            currentUserId = currentUserId,
                            currentUserName = userProfile?.displayName ?: currentUser?.displayName ?: "User",
                            downloadProgress = downloadProgress,
                            onBack = { currentScreen = StoreNavScreen.HOME },
                            onRequireSignIn = openGoogleSignInPrompt,
                            onToggleSave = {
                                val uid = currentUserId ?: return@AppDetailsScreen
                                coroutineScope.launch {
                                    if (isSaved) {
                                        repository.removeSavedApp(uid, currentApp.appId)
                                        snackbarHostState.showSnackbar("Removed from saved library")
                                    } else {
                                        repository.saveApp(uid, currentApp)
                                        snackbarHostState.showSnackbar("Saved to your library")
                                    }
                                }
                            },
                            onStartDownload = {
                                downloadController.startDownload(currentApp, coroutineScope, currentUserId)
                            },
                            onCancelDownload = {
                                downloadController.cancelDownload()
                            },
                            onInstallApk = { file ->
                                downloadController.installApk(file)
                            },
                            onOpenBrowserDownload = {
                                downloadController.startBrowserDownload(
                                    app = currentApp,
                                    coroutineScope = coroutineScope,
                                    currentUserId = currentUserId,
                                    onError = { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(error)
                                        }
                                    }
                                )
                            },
                            onSubmitReview = { rating, comment, existingId ->
                                coroutineScope.launch {
                                    val res = repository.submitReview(
                                        appId = currentApp.appId,
                                        rating = rating,
                                        comment = comment,
                                        userName = userProfile?.displayName ?: currentUser?.displayName ?: "User",
                                        userAvatar = userProfile?.avatarUrl ?: currentUser?.photoUrl?.toString(),
                                        existingReviewId = existingId
                                    )
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Review submitted successfully!")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to submit review: ${res.exceptionOrNull()?.localizedMessage}")
                                    }
                                }
                            },
                            onDeleteReview = { reviewId ->
                                coroutineScope.launch {
                                    repository.deleteReview(reviewId, currentApp.appId)
                                    snackbarHostState.showSnackbar("Review deleted")
                                }
                            }
                        )
                    } else {
                        currentScreen = StoreNavScreen.HOME
                    }
                }

                StoreNavScreen.ADMIN_LOGIN -> {
                    AdminLoginScreen(
                        currentUser = currentUser,
                        onBack = {
                            currentScreen = if (previousScreen == StoreNavScreen.PROFILE) {
                                StoreNavScreen.PROFILE
                            } else {
                                StoreNavScreen.HOME
                            }
                        },
                        onLoginSuccess = {
                            adminSessionVersion++
                            currentScreen = StoreNavScreen.UPLOAD
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Welcome, Administrator ${AdminAuthManager.TARGET_ADMIN_USERNAME}")
                            }
                        }
                    )
                }

                StoreNavScreen.UPLOAD -> {
                    BackHandler { currentScreen = StoreNavScreen.HOME }
                    UploadAppScreen(
                        currentUserId = currentUserId ?: "",
                        repository = repository,
                        deviceUpdateRepository = deviceUpdateRepository,
                        onBack = { currentScreen = StoreNavScreen.HOME },
                        onUploadSuccess = { appId: String ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Application published to STORE PRO!")
                            }
                            val created = applications.find { it.appId == appId }
                            if (created != null) {
                                selectedApp = created
                                currentScreen = StoreNavScreen.DETAILS
                            } else {
                                currentScreen = StoreNavScreen.HOME
                            }
                        },
                        onAdminLogout = {
                            adminSessionVersion++
                            currentScreen = StoreNavScreen.HOME
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Admin logged out")
                            }
                        },
                        onUnauthorized = {
                            adminSessionVersion++
                            currentScreen = StoreNavScreen.ADMIN_LOGIN
                        }
                    )
                }

                StoreNavScreen.PROFILE -> {
                    val myUploads = remember(applications, isAdminAuthorized, currentUserId) {
                        if (isAdminAuthorized && currentUserId != null) {
                            applications.filter { it.uploadedBy == currentUserId }
                        } else {
                            emptyList()
                        }
                    }

                    ProfileScreen(
                        isSignedIn = currentUserId != null,
                        isAdminAuthorized = isAdminAuthorized,
                        isServerAdminVerified = isServerAdminVerified,
                        registeredDevices = liveRegisteredDevices,
                        notificationsEnabled = hasNotificationPermission,
                        userProfile = userProfile,
                        myUploads = myUploads,
                        downloads = downloads,
                        savedApps = savedApps,
                        onSelectApp = { app ->
                            selectedApp = app
                            currentScreen = StoreNavScreen.DETAILS
                        },
                        onEditApp = { app ->
                            if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
                                previousScreen = StoreNavScreen.PROFILE
                                currentScreen = StoreNavScreen.ADMIN_LOGIN
                            } else {
                                editingApp = app
                            }
                        },
                        onDeleteApp = { appId ->
                            if (!AdminAuthManager.isFullyAuthorizedAdmin(context, Firebase.auth.currentUser)) {
                                previousScreen = StoreNavScreen.PROFILE
                                currentScreen = StoreNavScreen.ADMIN_LOGIN
                            } else {
                                coroutineScope.launch {
                                    val res = repository.deleteApplication(appId)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Application removed from STORE PRO")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to delete application")
                                    }
                                }
                            }
                        },
                        onToggleDeviceBlock = { device ->
                            coroutineScope.launch {
                                val nextBlocked = !device.isBlocked
                                val res = deviceUpdateRepository.setDeviceBlocked(device.installationId, nextBlocked)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar(
                                        if (nextBlocked) "Blocked ${device.deviceName}" else "Restored access for ${device.deviceName}"
                                    )
                                } else {
                                    snackbarHostState.showSnackbar("Failed to update device status")
                                }
                            }
                        },
                        onDeleteDevice = { installationId ->
                            coroutineScope.launch {
                                val res = deviceUpdateRepository.deleteRegisteredDevice(installationId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Device removed from registry")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to delete device record")
                                }
                            }
                        },
                        onEnableNotifications = triggerNotificationPermissionFlow,
                        onOpenUpload = openAdminProtectedUpload,
                        onGoogleSignIn = {
                            AuthManager.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Signed in with Google")
                                    }
                                },
                                onAuthError = { err ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(err)
                                    }
                                },
                                scope = coroutineScope
                            )
                        },
                        onAdminLogout = {
                            AdminAuthManager.logout(context)
                            adminSessionVersion++
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Admin session logged out")
                            }
                        },
                        onSignOut = {
                            adminSessionVersion++
                            onSignOut()
                        }
                    )
                }

                StoreNavScreen.AUTH_PROMPT -> {
                    AuthScreen(
                        promptMessage = authPromptMessage,
                        onAuthSuccess = {
                            currentScreen = previousScreen
                        },
                        onContinueAsGuest = {
                            currentScreen = previousScreen
                        }
                    )
                }

                StoreNavScreen.LIVE_UPDATE -> {
                    LiveUpdateDashboardScreen(
                        state = liveUpdateState,
                        canAccessAdminTools = canAccessAdminTools,
                        onBack = { currentScreen = StoreNavScreen.HOME },
                        onRefresh = {
                            coroutineScope.launch { liveUpdateController.refreshUpdates() }
                        },
                        onStartDownload = {
                            liveUpdateController.startDownload(coroutineScope)
                        },
                        onCancelDownload = {
                            liveUpdateController.cancelDownload()
                        },
                        onRetryDownload = {
                            liveUpdateController.retryDownload(coroutineScope)
                        },
                        onInstallUpdate = {
                            liveUpdateController.launchSystemInstaller()
                                .onSuccess { startedInstaller ->
                                    if (!startedInstaller) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Enable \"Allow from this source\" for STORE PRO, then tap Install Update.")
                                        }
                                    }
                                }
                                .onFailure { err ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Unable to launch package installer.")
                                    }
                                }
                        },
                        onOpenAdminManager = {
                            if (canAccessAdminTools) {
                                currentScreen = StoreNavScreen.ADMIN_LIVE_UPDATE
                            } else {
                                previousScreen = StoreNavScreen.LIVE_UPDATE
                                currentScreen = StoreNavScreen.ADMIN_LOGIN
                            }
                        }
                    )
                }

                StoreNavScreen.ADMIN_LIVE_UPDATE -> {
                    AdminLiveUpdateScreen(
                        allReleases = adminAllLiveReleases,
                        liveUpdateRepository = liveUpdateRepository,
                        deviceUpdateRepository = deviceUpdateRepository,
                        onBack = { currentScreen = StoreNavScreen.LIVE_UPDATE },
                        onUnauthorized = {
                            adminSessionVersion++
                            previousScreen = StoreNavScreen.LIVE_UPDATE
                            currentScreen = StoreNavScreen.ADMIN_LOGIN
                        },
                        onStatusMessage = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }
            }
            }
        }
    }

    // Edit Application Dialog (Admin Only)
    val appToEdit = editingApp
    if (appToEdit != null) {
        EditAppDialog(
            app = appToEdit,
            onDismiss = { editingApp = null },
            onSave = { updated ->
                coroutineScope.launch {
                    val res = repository.updateApplication(updated)
                    if (res.isSuccess) {
                        snackbarHostState.showSnackbar("Application updated successfully")
                    } else {
                        snackbarHostState.showSnackbar("Failed to update application")
                    }
                    editingApp = null
                }
            }
        )
    }

}

@Composable
private fun NotificationPermissionBanner(
    onEnable: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoreProCardBackground),
        border = BorderStroke(1.dp, StoreProCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("notification_permission_banner")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "New App Alerts",
                    tint = StoreProSecondaryCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Get New App Release Alerts",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Turn on notifications to get instant alerts whenever new applications are published on STORE PRO.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp).testTag("notification_banner_later_button")
                ) {
                    Text("Not Now", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoreProPrimaryGreen,
                        contentColor = Color(0xFF003816)
                    ),
                    modifier = Modifier.height(48.dp).testTag("notification_banner_enable_button")
                ) {
                    Text("Enable", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
