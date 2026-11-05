package com.example.myoutfit.ui.social

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.myoutfit.data.remote.social.CommentDto
import com.example.myoutfit.data.remote.social.FeedPostDto
import com.example.myoutfit.data.remote.social.FollowRequestItemDto
import com.example.myoutfit.data.remote.social.SearchedUserDto
import com.example.myoutfit.data.remote.social.SocialSession
import com.example.myoutfit.data.remote.social.UserNotificationDto
import com.example.myoutfit.data.remote.social.UserProfileDto
import com.example.myoutfit.ui.closet.PRESET_STYLE_TAGS
import com.example.myoutfit.util.UriUtils
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    viewModel: SocialFeedViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPublishUri by remember { mutableStateOf<String?>(null) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Reloj reactivo por segundo para actualizar las cuentas atrás de 24h en tiempo real
    var nowEpochSeconds by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(uiState.session.isAuthenticated) {
        while (uiState.session.isAuthenticated) {
            delay(1_000L)
            nowEpochSeconds = Instant.now().epochSecond
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCaptureUri != null) {
            selectedPublishUri = pendingCaptureUri.toString()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = UriUtils.createTempImageUri(context)
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPublishUri = uri.toString()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* resultado permiso notificaciones */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.posts) {
        viewModel.scheduleExpirationIfNeeded(context)
    }

    fun launchOutfitCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = UriUtils.createTempImageUri(context)
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Outfits 24h",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (uiState.session.isAuthenticated) {
                                Text(
                                    text = "@${uiState.session.username} · Racha 🔥 ${uiState.session.currentStreak}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState.session.isAuthenticated) {
                        IconButton(onClick = {
                            showFriendsDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = "Buscar personas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        val totalNotifsCount = uiState.pendingRequests.size + uiState.notifications.count { it.readAt == null }
                        IconButton(onClick = {
                            viewModel.loadFollowRequests()
                            showNotificationsDialog = true
                        }) {
                            BadgedBox(
                                badge = {
                                    if (totalNotifsCount > 0) {
                                        Badge { Text("$totalNotifsCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notificaciones y Solicitudes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refreshFeed() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar feed"
                            )
                        }
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar sesión"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.session.isAuthenticated) {
                FloatingActionButton(
                    onClick = { launchOutfitCamera() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Subir Outfit del Día"
                    )
                }
            }
        }
    ) { innerPadding ->
        if (!uiState.session.isAuthenticated) {
            SocialAuthScreen(
                isSubmitting = uiState.isAuthSubmitting,
                resetCodePendingEmail = uiState.resetCodePendingEmail,
                passwordResetCompleted = uiState.passwordResetCompleted,
                onLogin = { identifier, pass -> viewModel.login(identifier, pass) },
                onRegister = { user, email, pass, display, isPriv ->
                    viewModel.register(user, email, pass, display, isPriv)
                },
                onGoogleIdToken = { idToken -> viewModel.loginWithGoogle(idToken) },
                onGoogleError = { msg -> viewModel.showMessage(msg) },
                onRequestPasswordReset = { email -> viewModel.requestPasswordReset(email) },
                onConfirmPasswordReset = { email, code, newPass ->
                    viewModel.confirmPasswordReset(email, code, newPass)
                },
                onCancelPasswordReset = { viewModel.cancelPasswordReset() },
                onConsumePasswordResetCompleted = { viewModel.consumePasswordResetCompleted() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Selector Seguidos vs Descubrir
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !uiState.discoverMode,
                            onClick = { viewModel.setDiscoverMode(false) },
                            label = { Text("Mis Seguidos") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = uiState.discoverMode,
                            onClick = { viewModel.setDiscoverMode(true) },
                            label = { Text("Explorar Público") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        if (uiState.isLoading) {
                            Spacer(modifier = Modifier.weight(1f))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // Banner estilo BeReal si aún no publicó su outfit hoy
                if (!uiState.hasPostedToday) {
                    item {
                        DailyPromptBanner(
                            onCaptureCamera = { launchOutfitCamera() },
                            onPickGallery = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                if (uiState.posts.isEmpty() && !uiState.isLoading) {
                    item {
                        EmptyFeedCard(
                            discoverMode = uiState.discoverMode,
                            onFindFriends = { showFriendsDialog = true },
                            onShareOutfit = { launchOutfitCamera() }
                        )
                    }
                } else {
                    items(
                        items = uiState.posts,
                        key = { it.postId }
                    ) { post ->
                        EphemeralOutfitCard(
                            post = post,
                            session = uiState.session,
                            nowEpochSeconds = nowEpochSeconds,
                            onReact = { emoji -> viewModel.toggleReaction(post, emoji) },
                            onOpenComments = { viewModel.openComments(post) },
                            onOpenUserProfile = { userId -> viewModel.openUserProfile(userId) },
                            onToggleFollow = { id, name, status, isPriv ->
                                viewModel.toggleFollowUser(id, name, status, isPriv)
                            },
                            onDeletePost = { postId -> viewModel.deleteMyPost(postId) },
                            onReportPost = { postId, reason, details ->
                                viewModel.reportPost(postId, reason, details)
                            },
                            onBlockUser = { targetUserId, targetUsername ->
                                viewModel.blockUser(targetUserId, targetUsername, true)
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (selectedPublishUri != null) {
        PublishOutfitDialog(
            imageUri = selectedPublishUri!!,
            isPublishing = uiState.isPublishing,
            onDismiss = { if (!uiState.isPublishing) selectedPublishUri = null },
            onPublish = { caption, visibility, styleTags ->
                viewModel.publishDailyOutfit(
                    context = context,
                    imageUriOrPath = selectedPublishUri!!,
                    caption = caption,
                    sourceType = "REAL_CAMERA",
                    visibility = visibility,
                    styleTags = styleTags
                )
                selectedPublishUri = null
            }
        )
    }

    if (showFriendsDialog) {
        SearchUsersDialog(
            searchedUsers = uiState.searchedUsers,
            isSearching = uiState.isSearchingUsers,
            onSearchQueryChange = { viewModel.searchUsers(it) },
            onToggleFollow = { viewModel.toggleFollowUser(it) },
            onOpenUserProfile = { userId ->
                showFriendsDialog = false
                viewModel.openUserProfile(userId)
            },
            onDismiss = { showFriendsDialog = false }
        )
    }

    if (showNotificationsDialog) {
        NotificationsDialog(
            pendingRequests = uiState.pendingRequests,
            notifications = uiState.notifications,
            onRespondRequest = { id, accept -> viewModel.respondToFollowRequest(id, accept) },
            onOpenUserProfile = { userId ->
                showNotificationsDialog = false
                viewModel.openUserProfile(userId)
            },
            onDismiss = { showNotificationsDialog = false }
        )
    }

    if (uiState.activeCommentsPost != null) {
        CommentsBottomSheet(
            post = uiState.activeCommentsPost!!,
            comments = uiState.activeComments,
            isLoading = uiState.isLoadingComments,
            session = uiState.session,
            onSendComment = { viewModel.addComment(it) },
            onOpenUserProfile = { userId ->
                viewModel.openUserProfile(userId)
            },
            onDismiss = { viewModel.closeComments() }
        )
    }

    if (uiState.selectedUserProfile != null || uiState.isLoadingUserProfile) {
        UserProfileBottomSheet(
            profile = uiState.selectedUserProfile,
            isLoading = uiState.isLoadingUserProfile,
            session = uiState.session,
            nowEpochSeconds = nowEpochSeconds,
            onReactToPost = { post, emoji -> viewModel.toggleReaction(post, emoji) },
            onOpenComments = { post -> viewModel.openComments(post) },
            onToggleFollow = { id, name, status, isPriv ->
                viewModel.toggleFollowUser(id, name, status, isPriv)
            },
            onDeletePost = { postId ->
                viewModel.deleteMyPost(postId)
                viewModel.closeUserProfile()
            },
            onReportPost = { postId, reason, details ->
                viewModel.reportPost(postId, reason, details)
                viewModel.closeUserProfile()
            },
            onBlockUser = { targetUserId, targetUsername, block ->
                viewModel.blockUser(targetUserId, targetUsername, block)
                viewModel.closeUserProfile()
            },
            onDismiss = { viewModel.closeUserProfile() }
        )
    }
}

@Composable
private fun DailyPromptBanner(
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "¡Outfit del Día!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Comparte tu estilo antes de que expire el día ⏳",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCaptureCamera,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cámara", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onPickGallery,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galería", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedCard(
    discoverMode: Boolean,
    onFindFriends: () -> Unit,
    onShareOutfit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (discoverMode) Icons.Default.Public else Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = if (discoverMode) {
                    "No hay outfits públicos ahora"
                } else {
                    "Tus amigos no han publicado hoy"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = if (discoverMode) {
                    "Sé el primero en compartir tu outfit público de hoy."
                } else {
                    "¡Rompe el hielo compartiendo tu outfit o busca a tus amigos!"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!discoverMode) {
                    OutlinedButton(
                        onClick = onFindFriends,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buscar amigos")
                    }
                }
                Button(
                    onClick = onShareOutfit,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publicar ahora")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EphemeralOutfitCard(
    post: FeedPostDto,
    session: SocialSession,
    nowEpochSeconds: Long,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onToggleFollow: (targetUserId: String, targetUsername: String, currentFollowStatus: String, isTargetPrivate: Boolean) -> Unit,
    onDeletePost: (String) -> Unit,
    onReportPost: (String, String, String) -> Unit,
    onBlockUser: (targetUserId: String, targetUsername: String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val expiresEpoch = remember(post.expiresAt) {
        runCatching { Instant.parse(post.expiresAt).epochSecond }.getOrNull()
    }
    val liveRemainingSeconds = remember(expiresEpoch, nowEpochSeconds, post.remainingSeconds) {
        if (expiresEpoch != null) {
            (expiresEpoch - nowEpochSeconds).coerceAtLeast(0L).toInt()
        } else {
            post.remainingSeconds.coerceAtLeast(0)
        }
    }
    val lifeFraction = (liveRemainingSeconds / 86_400f).coerceIn(0f, 1f)
    val isLiked = post.myReaction != null

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Barra de vida efímera de 24h
            LinearProgressIndicator(
                progress = { lifeFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = if (liveRemainingSeconds < 3600) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            // Cabecera del autor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenUserProfile(post.author.id) }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                ) {
                    val avatarUrl = session.resolveMediaUrl(post.author.avatarUrl)
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = post.author.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = post.author.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.author.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (post.author.isPrivate) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Cuenta privada",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${post.author.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (post.isLate) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "Tarde",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (post.author.id != session.userId) {
                        Spacer(modifier = Modifier.width(10.dp))
                        when (post.author.followStatus) {
                            "ACCEPTED" -> {
                                OutlinedButton(
                                    onClick = {
                                        onToggleFollow(
                                            post.author.id,
                                            post.author.username,
                                            post.author.followStatus,
                                            post.author.isPrivate
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Siguiendo", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            "PENDING" -> {
                                FilledTonalButton(
                                    onClick = {
                                        onToggleFollow(
                                            post.author.id,
                                            post.author.username,
                                            post.author.followStatus,
                                            post.author.isPrivate
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Pendiente ⏳", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        onToggleFollow(
                                            post.author.id,
                                            post.author.username,
                                            post.author.followStatus,
                                            post.author.isPrivate
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("+ Seguir", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Píldora de cuenta atrás
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (liveRemainingSeconds < 3600) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (liveRemainingSeconds < 3600) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatCountdown(liveRemainingSeconds),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (liveRemainingSeconds < 3600) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }

                    // Menú de opciones (Art. 17 RGPD Supresión / DSA Denuncia)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (post.author.id == session.userId) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Suprimir foto (Art. 17 RGPD)",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Denunciar publicación (DSA)",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showReportDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Bloquear a @${post.author.username}",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showBlockDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Foto del Outfit vertical 3:4 con soporte para doble toque para dar Me Gusta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onReact(if (isLiked) (post.myReaction ?: "❤️") else "❤️")
                            }
                        )
                    }
            ) {
                AsyncImage(
                    model = session.resolveMediaUrl(post.imageUrl),
                    contentDescription = post.caption.ifBlank { "Outfit de ${post.author.username}" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Estilos / Tags flotantes en la imagen
                if (post.styleTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        post.styleTags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Barra de acciones moderna (Me gusta + Comentarios)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Botón único de Me Gusta moderno con corazón
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                onReact(if (isLiked) (post.myReaction ?: "❤️") else "❤️")
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Ya no me gusta" else "Me gusta",
                            tint = if (isLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (post.reactionsCount > 0) "${post.reactionsCount}" else "Me gusta",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Botón de Comentarios
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onOpenComments() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Ver comentarios",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (post.commentsCount > 0) "${post.commentsCount}" else "Comentar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Pie de publicación: Caption del usuario y enlace a comentarios
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (post.caption.isNotBlank()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("@${post.author.username} ")
                            }
                            append(post.caption)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (post.commentsCount > 0) {
                    Text(
                        text = "Ver los ${post.commentsCount} comentarios...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onOpenComments() }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Derecho de supresión (RGPD)", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Conforme al Art. 17 del RGPD (Derecho al Olvido), al confirmar se eliminará de forma inmediata y definitiva esta fotografía de los outfits de 24 horas, del calendario y de los servidores de MyOutfit.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePost(post.postId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Suprimir definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showReportDialog) {
        ReportPostDialog(
            postAuthor = post.author.username,
            onConfirmReport = { reason, details ->
                showReportDialog = false
                onReportPost(post.postId, reason, details)
            },
            onDismiss = { showReportDialog = false }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Bloquear a @${post.author.username}", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Al bloquear a @${post.author.username}, no podrás ver sus publicaciones ni su perfil, y tampoco podrá ver las tuyas. Se cancelará cualquier seguimiento mutuo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        onBlockUser(post.author.id, post.author.username)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Bloquear usuario")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublishOutfitDialog(
    imageUri: String,
    isPublishing: Boolean,
    onDismiss: () -> Unit,
    onPublish: (caption: String, visibility: String, styleTags: List<String>) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("FOLLOWERS") }
    val selectedTags = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Publicar Outfit del Día",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Previsualización",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Pie de foto (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Visibilidad durante 24h:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = visibility == "FOLLOWERS",
                        onClick = { visibility = "FOLLOWERS" },
                        label = { Text("Amigos") }
                    )
                    FilterChip(
                        selected = visibility == "PUBLIC",
                        onClick = { visibility = "PUBLIC" },
                        label = { Text("Público") }
                    )
                    FilterChip(
                        selected = visibility == "PRIVATE_ONLY",
                        onClick = { visibility = "PRIVATE_ONLY" },
                        label = { Text("Solo Calendario") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }

                Text(
                    text = "Estilos (para tu Yearly Recap):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PRESET_STYLE_TAGS.forEach { tag ->
                        val active = tag in selectedTags
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedTags.remove(tag) else selectedTags.add(tag)
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(caption, visibility, selectedTags.toList()) },
                enabled = !isPublishing
            ) {
                Text(if (isPublishing) "Subiendo..." else "Publicar 24h")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun SearchUsersDialog(
    searchedUsers: List<SearchedUserDto>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleFollow: (SearchedUserDto) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar personas", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchQueryChange(it)
                    },
                    label = { Text("Buscar por @usuario o nombre") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (query.trim().length >= 2 && searchedUsers.isEmpty()) {
                    Text(
                        text = "No se encontraron usuarios para \"$query\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                searchedUsers.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenUserProfile(user.id) }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Text(user.displayName, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (user.isPrivate) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "🔒 Privada",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                        when (user.followStatus) {
                            "ACCEPTED" -> {
                                OutlinedButton(onClick = { onToggleFollow(user) }) {
                                    Text("Siguiendo")
                                }
                            }
                            "PENDING" -> {
                                FilledTonalButton(onClick = { onToggleFollow(user) }) {
                                    Text("Pendiente ⏳")
                                }
                            }
                            else -> {
                                Button(onClick = { onToggleFollow(user) }) {
                                    Text("+ Seguir")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun NotificationsDialog(
    pendingRequests: List<FollowRequestItemDto>,
    notifications: List<UserNotificationDto>,
    onRespondRequest: (String, Boolean) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Notificaciones",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (pendingRequests.isEmpty() && notifications.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No tienes avisos ni solicitudes pendientes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Avisos del sistema / caducidad de outfits 24h
                if (notifications.isNotEmpty()) {
                    Text(
                        text = "Avisos de Outfits 24h",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    notifications.forEach { notif ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        val timeAgo = formatCommentTime(notif.createdAt)
                                        if (timeAgo.isNotBlank()) {
                                            Text(
                                                text = timeAgo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Solicitudes de seguimiento
                if (pendingRequests.isNotEmpty()) {
                    Text(
                        text = "Solicitudes para seguirte (${pendingRequests.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Al aceptar, esta persona podrá ver tus publicaciones diarias de 24h.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pendingRequests.forEach { req ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenUserProfile(req.userId) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(req.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "@${req.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onRespondRequest(req.userId, false) }) {
                                    Text("Rechazar")
                                }
                                Button(onClick = { onRespondRequest(req.userId, true) }) {
                                    Text("Aceptar")
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    post: FeedPostDto,
    comments: List<CommentDto>,
    isLoading: Boolean,
    session: SocialSession,
    onSendComment: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Comentarios",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Outfit de @${post.author.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${comments.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Lista de comentarios con avatares
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (comments.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Aún no hay comentarios",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "¡Sé el primero en opinar sobre este outfit!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(comments, key = { it.id }) { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Foto de perfil del usuario que comenta
                                val avatarUrl = session.resolveMediaUrl(c.author.avatarUrl)
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = c.author.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { onOpenUserProfile(c.author.id) }
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { onOpenUserProfile(c.author.id) }
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = c.author.displayName.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                // Contenido del comentario
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenUserProfile(c.author.id) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "@${c.author.username}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val timeStr = formatCommentTime(c.createdAt)
                                        if (timeStr.isNotBlank()) {
                                            Text(
                                                text = "· $timeStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = c.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Campo de entrada moderno para escribir y enviar comentario
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val myAvatar = session.resolveMediaUrl(session.avatarUrl)
                if (myAvatar != null) {
                    AsyncImage(
                        model = myAvatar,
                        contentDescription = "Mi perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.username.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Añade un comentario...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotBlank()) {
                            onSendComment(trimmed)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar comentario",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun formatCommentTime(isoString: String): String {
    return runCatching {
        val instant = Instant.parse(isoString)
        val now = Instant.now()
        val diffSeconds = (now.epochSecond - instant.epochSecond).coerceAtLeast(0)
        when {
            diffSeconds < 60 -> "ahora"
            diffSeconds < 3600 -> "${diffSeconds / 60}m"
            diffSeconds < 86400 -> "${diffSeconds / 3600}h"
            else -> "${diffSeconds / 86400}d"
        }
    }.getOrDefault("")
}

private fun formatCountdown(seconds: Int): String {
    if (seconds <= 0) return "Expirado"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m ${secs}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserProfileBottomSheet(
    profile: UserProfileDto?,
    isLoading: Boolean,
    session: SocialSession,
    nowEpochSeconds: Long,
    onReactToPost: (FeedPostDto, String) -> Unit,
    onOpenComments: (FeedPostDto) -> Unit,
    onToggleFollow: (targetUserId: String, targetUsername: String, currentFollowStatus: String, isTargetPrivate: Boolean) -> Unit,
    onDeletePost: (String) -> Unit,
    onReportPost: (String, String, String) -> Unit,
    onBlockUser: (targetUserId: String, targetUsername: String, block: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBlockConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && profile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else if (profile != null) {
                // Cabecera: Avatar grande con borde suave
                val avatarUrl = session.resolveMediaUrl(profile.avatarUrl)
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Nombre y @username
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (profile.isPrivate) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Cuenta privada",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "@${profile.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bio
                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Métricas (Outfits, Racha, Seguidores, Seguidos)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileStatItem(count = "${profile.totalOutfitsCount}", label = "Outfits")
                    ProfileStatItem(count = "🔥 ${profile.currentStreak}d", label = "Racha")
                    ProfileStatItem(count = "${profile.followersCount}", label = "Seguidores")
                    ProfileStatItem(count = "${profile.followingCount}", label = "Seguidos")
                }

                // Botones de acción (Seguir / Bloquear)
                if (profile.id != session.userId) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile.isBlockedByMe) {
                            OutlinedButton(
                                onClick = {
                                    onBlockUser(profile.id, profile.username, false)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Desbloquear usuario")
                            }
                        } else {
                            when (profile.followStatus) {
                                "ACCEPTED" -> {
                                    OutlinedButton(
                                        onClick = {
                                            onToggleFollow(
                                                profile.id,
                                                profile.username,
                                                profile.followStatus,
                                                profile.isPrivate
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(0.6f)
                                    ) {
                                        Text("Siguiendo")
                                    }
                                }
                                "PENDING" -> {
                                    FilledTonalButton(
                                        onClick = {
                                            onToggleFollow(
                                                profile.id,
                                                profile.username,
                                                profile.followStatus,
                                                profile.isPrivate
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(0.6f)
                                    ) {
                                        Text("Solicitud pendiente ⏳")
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            onToggleFollow(
                                                profile.id,
                                                profile.username,
                                                profile.followStatus,
                                                profile.isPrivate
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth(0.6f)
                                    ) {
                                        Text("+ Seguir")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = { showBlockConfirmDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Bloquear a @${profile.username}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Sección del Outfit de 24h
                if (!profile.canViewPosts) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Esta cuenta es privada",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Sigue a @${profile.username} para ver sus fotos y outfits de 24 horas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (profile.activePost != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Outfit activo de 24h ⏳",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        EphemeralOutfitCard(
                            post = profile.activePost,
                            session = session,
                            nowEpochSeconds = nowEpochSeconds,
                            onReact = { emoji -> onReactToPost(profile.activePost, emoji) },
                            onOpenComments = { onOpenComments(profile.activePost) },
                            onOpenUserProfile = { /* ya está en el perfil */ },
                            onToggleFollow = onToggleFollow,
                            onDeletePost = onDeletePost,
                            onReportPost = onReportPost,
                            onBlockUser = { targetId, targetUsername ->
                                onBlockUser(targetId, targetUsername, true)
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sin outfit activo hoy",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "@${profile.username} aún no ha publicado en las últimas 24h.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showBlockConfirmDialog && profile != null) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Bloquear a @${profile.username}", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Al bloquear a @${profile.username}, no podrás ver sus publicaciones ni su perfil, y tampoco podrá ver las tuyas. Se cancelará cualquier seguimiento mutuo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        onBlockUser(profile.id, profile.username, true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Bloquear usuario")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProfileStatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportPostDialog(
    postAuthor: String,
    onConfirmReport: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("INAPPROPRIATE") }
    var details by remember { mutableStateOf("") }

    val reasons = listOf(
        "INAPPROPRIATE" to "Contenido inapropiado / ofensivo",
        "HARASSMENT" to "Acoso o intimidación",
        "PRIVACY" to "Vulneración de privacidad / imagen",
        "SPAM" to "Spam o engaño publicitario",
        "COPYRIGHT" to "Infracción de derechos de autor",
        "OTHER" to "Otro motivo"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text("Denunciar publicación (DSA)", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Conforme al Art. 16 de la Ley de Servicios Digitales (DSA - Reglamento UE 2022/2065), revisaremos la publicación de @$postAuthor. Si vulnera la legalidad o las normas comunitarias, se retirará de forma preventiva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Selecciona el motivo principal:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                reasons.forEach { (code, label) ->
                    val isSelected = selectedReason == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = code }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Text("✓", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Detalles adicionales (opcional)") },
                    placeholder = { Text("Describe el motivo...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReport(selectedReason, details.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Enviar denuncia")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
