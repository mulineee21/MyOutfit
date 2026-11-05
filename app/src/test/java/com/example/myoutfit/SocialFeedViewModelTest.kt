package com.example.myoutfit

import android.content.Context
import com.example.myoutfit.data.remote.social.CalendarEntryDto
import com.example.myoutfit.data.remote.social.CalendarMonthResponseDto
import com.example.myoutfit.data.remote.social.CommentDto
import com.example.myoutfit.data.remote.social.CreatedPostDto
import com.example.myoutfit.data.remote.social.FeedPostDto
import com.example.myoutfit.data.remote.social.FeedResponseDto
import com.example.myoutfit.data.remote.social.FollowRequestItemDto
import com.example.myoutfit.data.remote.social.MeProfileDto
import com.example.myoutfit.data.remote.social.PostAuthorDto
import com.example.myoutfit.data.remote.social.SearchedUserDto
import com.example.myoutfit.data.remote.social.SocialSession
import com.example.myoutfit.data.remote.social.StyleUsageDto
import com.example.myoutfit.data.remote.social.UserNotificationDto
import com.example.myoutfit.data.remote.social.UserProfileDto
import com.example.myoutfit.data.remote.social.YearlyRecapDto
import com.example.myoutfit.data.repository.SocialRepository
import com.example.myoutfit.ui.profile.ProfileViewModel
import com.example.myoutfit.ui.social.SocialFeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class FakeSocialRepository : SocialRepository {
    private val _session = MutableStateFlow(SocialSession())
    override val sessionState: StateFlow<SocialSession> = _session

    var feedItems: List<FeedPostDto> = emptyList()
    var calendarEntries: List<CalendarEntryDto> = emptyList()
    var lastResetEmail: String? = null
    var expectedResetCode: String = "654321"

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        displayName: String,
        isPrivate: Boolean
    ): Result<Unit> {
        _session.value = _session.value.copy(
            accessToken = "fake-jwt-access",
            refreshToken = "fake-jwt-refresh",
            userId = "00000000-0000-0000-0000-000000000001",
            username = username,
            displayName = displayName,
            isPrivate = isPrivate
        )
        return Result.success(Unit)
    }

    override suspend fun login(identifier: String, password: String): Result<Unit> {
        _session.value = _session.value.copy(
            accessToken = "fake-jwt-access",
            refreshToken = "fake-jwt-refresh",
            userId = "00000000-0000-0000-0000-000000000001",
            username = identifier,
            displayName = "Joel",
            currentStreak = 4
        )
        return Result.success(Unit)
    }

    override suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        if (idToken.isBlank()) return Result.failure(IllegalArgumentException("Token vacío"))
        _session.value = _session.value.copy(
            accessToken = "fake-google-jwt-access",
            refreshToken = "fake-google-jwt-refresh",
            userId = "00000000-0000-0000-0000-000000000002",
            username = "joel_google",
            displayName = "Joel Google",
            currentStreak = 1
        )
        return Result.success(Unit)
    }

    override suspend fun requestPasswordReset(email: String): Result<String> {
        lastResetEmail = email
        return Result.success("Código enviado")
    }

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): Result<String> {
        return if (email == lastResetEmail && code == expectedResetCode && newPassword.length >= 8) {
            Result.success("Contraseña actualizada")
        } else {
            Result.failure(IllegalArgumentException("Código inválido o caducado"))
        }
    }

    override suspend fun logout() {
        _session.value = SocialSession(serverBaseUrl = _session.value.serverBaseUrl)
    }

    override suspend fun getFeed(discover: Boolean): Result<FeedResponseDto> {
        return Result.success(
            FeedResponseDto(
                hasPostedToday = feedItems.isNotEmpty(),
                items = feedItems
            )
        )
    }

    override suspend fun publishDailyOutfit(
        context: Context,
        imageUriOrPath: String,
        caption: String,
        sourceType: String,
        visibility: String,
        styleTags: List<String>,
        dominantColors: List<String>,
        wornDate: String
    ): Result<CreatedPostDto> {
        return Result.failure(UnsupportedOperationException())
    }

    override suspend fun reactToPost(postId: String, emoji: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeReaction(postId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getComments(postId: String): Result<List<CommentDto>> =
        Result.success(emptyList())

    override suspend fun addComment(postId: String, content: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getMonthlyCalendar(year: Int, month: Int): Result<CalendarMonthResponseDto> {
        return Result.success(
            CalendarMonthResponseDto(
                userId = _session.value.userId,
                year = year,
                month = month,
                totalLoggedDaysInMonth = calendarEntries.size,
                entries = calendarEntries
            )
        )
    }

    override suspend fun getYearlyRecap(year: Int): Result<YearlyRecapDto> {
        return Result.success(
            YearlyRecapDto(
                year = year,
                totalOutfits = calendarEntries.size,
                uniqueDaysLogged = calendarEntries.size,
                topStyles = listOf(StyleUsageDto("Streetwear", 5))
            )
        )
    }

    override suspend fun searchUsers(query: String): Result<List<SearchedUserDto>> {
        return Result.success(
            listOf(
                SearchedUserDto(
                    id = "00000000-0000-0000-0000-000000000099",
                    username = "ana_style",
                    displayName = "Ana",
                    followStatus = "NONE"
                )
            )
        )
    }

    override suspend fun getUserProfile(userId: String): Result<UserProfileDto> {
        return Result.success(
            UserProfileDto(
                id = userId,
                username = "usuario_test",
                displayName = "Usuario Test",
                bio = "Bio de prueba",
                isPrivate = false,
                totalOutfitsCount = 10,
                currentStreak = 3,
                followersCount = 5,
                followingCount = 2,
                followStatus = "NONE",
                canViewPosts = true,
                activePost = null
            )
        )
    }

    override suspend fun toggleFollow(targetUserId: String, follow: Boolean): Result<String> {
        return Result.success(if (follow) "ACCEPTED" else "NONE")
    }

    override suspend fun getPendingFollowRequests(): Result<List<FollowRequestItemDto>> =
        Result.success(emptyList())

    override suspend fun getNotifications(): Result<List<UserNotificationDto>> =
        Result.success(emptyList())

    override suspend fun respondFollowRequest(followerId: String, accept: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun refreshMyProfile(): Result<MeProfileDto> {
        val s = _session.value
        if (!s.isAuthenticated) return Result.failure(IllegalStateException("No auth"))
        return Result.success(
            MeProfileDto(
                id = s.userId,
                username = s.username,
                email = s.email,
                displayName = s.displayName,
                bio = s.bio,
                avatarUrl = s.avatarUrl,
                isPrivate = s.isPrivate,
                currentStreak = s.currentStreak,
                canChangeUsername = s.canChangeUsername,
                nextUsernameChangeAt = s.nextUsernameChangeAt,
                usernameCooldownDaysRemaining = s.usernameCooldownDaysRemaining
            )
        )
    }

    override suspend fun updateMyUsername(
        newUsername: String
    ): Result<com.example.myoutfit.data.remote.social.UpdateUsernameResponseDto> {
        val s = _session.value
        if (!s.canChangeUsername) {
            return Result.failure(
                IllegalStateException("Solo puedes cambiar tu nombre de perfil 1 vez al mes.")
            )
        }
        val nextDate = "2026-12-05T06:00:00Z"
        _session.value = s.copy(
            username = newUsername,
            displayName = newUsername,
            canChangeUsername = false,
            nextUsernameChangeAt = nextDate,
            usernameCooldownDaysRemaining = 30
        )
        return Result.success(
            com.example.myoutfit.data.remote.social.UpdateUsernameResponseDto(
                username = newUsername,
                displayName = newUsername,
                canChangeUsername = false,
                nextUsernameChangeAt = nextDate,
                usernameCooldownDaysRemaining = 30,
                accessToken = "fresh-jwt"
            )
        )
    }

    override suspend fun updateMyProfile(
        displayName: String?,
        bio: String?,
        isPrivate: Boolean?
    ): Result<com.example.myoutfit.data.remote.social.UpdatedProfileDto> {
        val s = _session.value
        val updatedBio = bio ?: s.bio
        val updatedPriv = isPrivate ?: s.isPrivate
        _session.value = s.copy(bio = updatedBio, isPrivate = updatedPriv)
        return Result.success(
            com.example.myoutfit.data.remote.social.UpdatedProfileDto(
                id = s.userId,
                username = s.username,
                displayName = displayName ?: s.displayName,
                bio = updatedBio,
                isPrivate = updatedPriv
            )
        )
    }

    override suspend fun uploadMyAvatar(
        context: Context,
        imageUriOrPath: String
    ): Result<String> {
        val url = "/api/media/avatars/${_session.value.userId}"
        _session.value = _session.value.copy(avatarUrl = url)
        return Result.success(url)
    }

    var exportJsonPayload: String = """{"rgpdVersion":"2026.1-ES","user":{"username":"joel"}}"""
    var deleteAccountCalled: Boolean = false

    override suspend fun exportMyGdprDataJson(): Result<String> {
        return Result.success(exportJsonPayload)
    }

    override suspend fun deleteMyAccount(): Result<String> {
        deleteAccountCalled = true
        _session.value = SocialSession(serverBaseUrl = _session.value.serverBaseUrl)
        return Result.success("Cuenta eliminada correctamente conforme al Art. 17 RGPD.")
    }

    var deletedPostId: String? = null
    var reportedPostId: String? = null

    override suspend fun deletePost(postId: String, purgeArchive: Boolean): Result<String> {
        deletedPostId = postId
        feedItems = feedItems.filterNot { it.postId == postId }
        return Result.success("Fotografía eliminada conforme al Art. 17 RGPD.")
    }

    override suspend fun reportPost(postId: String, reason: String, details: String): Result<String> {
        reportedPostId = postId
        return Result.success("Denuncia registrada conforme a la Ley de Servicios Digitales (DSA).")
    }

    var blockedUserIds: MutableSet<String> = mutableSetOf()

    override suspend fun blockUser(targetUserId: String, block: Boolean): Result<String> {
        if (block) {
            blockedUserIds.add(targetUserId)
            feedItems = feedItems.filterNot { it.author.id == targetUserId }
        } else {
            blockedUserIds.remove(targetUserId)
        }
        return Result.success(if (block) "Usuario bloqueado" else "Usuario desbloqueado")
    }

    override suspend fun getBlockedUsers(): Result<List<com.example.myoutfit.data.remote.social.BlockedUserDto>> {
        return Result.success(
            blockedUserIds.map { id ->
                com.example.myoutfit.data.remote.social.BlockedUserDto(
                    id = id,
                    username = "user_$id",
                    displayName = "User $id",
                    avatarUrl = null,
                    blockedAt = "2026-11-05T00:00:00Z"
                )
            }
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SocialFeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSocialRepo: FakeSocialRepository
    private lateinit var viewModel: SocialFeedViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSocialRepo = FakeSocialRepository()
        fakeSocialRepo.feedItems = listOf(
            FeedPostDto(
                postId = "post-1",
                archiveId = "arch-1",
                author = PostAuthorDto(
                    id = "user-2",
                    username = "carlos_fit",
                    displayName = "Carlos"
                ),
                imageUrl = "/api/media/outfits/arch-1/full",
                thumbnailUrl = "/api/media/outfits/arch-1/thumb",
                caption = "Look urbano de miércoles",
                styleTags = listOf("Streetwear"),
                publishedAt = "2026-11-04T10:00:00Z",
                expiresAt = "2026-11-05T10:00:00Z",
                remainingSeconds = 72000
            )
        )
        viewModel = SocialFeedViewModel(fakeSocialRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_authenticatesTransparentlyAndLoadsActive24hFeed() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.session.isAuthenticated)
        assertEquals(1, state.posts.size)
        assertEquals("Look urbano de miércoles", state.posts.first().caption)
    }

    @Test
    fun loginWithGoogle_authenticatesWithIdTokenAndLoadsFeed() = runTest {
        viewModel.loginWithGoogle("valid-google-id-token")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.session.isAuthenticated)
        assertEquals("joel_google", state.session.username)
        assertEquals(1, state.posts.size)
    }

    @Test
    fun passwordRecoveryFlow_requestsCodeAndResetsPassword() = runTest {
        viewModel.requestPasswordReset("joel@example.com")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("joel@example.com", viewModel.uiState.value.resetCodePendingEmail)

        viewModel.confirmPasswordReset("joel@example.com", "654321", "NuevaPass2026!")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.resetCodePendingEmail)
        assertTrue(state.passwordResetCompleted)
    }

    @Test
    fun toggleReaction_updatesEmojiAndCountOptimistically() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()

        val post = viewModel.uiState.value.posts.first()
        viewModel.toggleReaction(post, "🔥")
        testDispatcher.scheduler.advanceUntilIdle()

        val afterAdd = viewModel.uiState.value.posts.first()
        assertEquals("🔥", afterAdd.myReaction)
        assertEquals(1, afterAdd.reactionsCount)

        viewModel.toggleReaction(afterAdd, "🔥")
        testDispatcher.scheduler.advanceUntilIdle()

        val afterRemove = viewModel.uiState.value.posts.first()
        assertNull(afterRemove.myReaction)
        assertEquals(0, afterRemove.reactionsCount)
    }

    @Test
    fun profileCalendar_mergesCloudPrivateArchiveEntries() = runTest {
        val currentYm = YearMonth.now()
        val sampleDate = "${currentYm.year}-${currentYm.monthValue.toString().padStart(2, '0')}-04"
        fakeSocialRepo.calendarEntries = listOf(
            CalendarEntryDto(
                archiveId = "arch-cal-4",
                wornDate = sampleDate,
                thumbnailUrl = "/api/media/outfits/arch-cal-4/thumb",
                fullImageUrl = "/api/media/outfits/arch-cal-4/full",
                caption = "Outfit archivado permanentemente",
                styleTags = listOf("Streetwear"),
                createdAt = "2026-11-04T12:00:00Z"
            )
        )
        fakeSocialRepo.login("joel_m", "secret1234")

        val profileVm = ProfileViewModel(
            userPreferencesRepository = FakeUserPreferencesRepository(),
            loggedOutfitRepository = FakeLoggedOutfitRepository(),
            outfitRepository = FakeOutfitRepository(),
            clothingRepository = FakeClothingRepository(),
            wishlistRepository = FakeWishlistRepository(),
            socialRepository = fakeSocialRepo
        )

        backgroundScope.launch { profileVm.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = profileVm.uiState.value
        assertTrue(state.isSocialConnected)
        assertTrue(4 in state.calendarDayEntries)
        assertEquals("Outfit archivado permanentemente", state.calendarDayEntries[4]?.caption)
        assertEquals(1, state.yearlyRecap?.uniqueDaysLogged)
    }

    @Test
    fun openUserProfile_loadsProfileAndAllowsClosing() = runTest {
        viewModel.openUserProfile("target-user-123")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("target-user-123", state.selectedUserProfile?.id)
        assertEquals("usuario_test", state.selectedUserProfile?.username)
        assertEquals(10, state.selectedUserProfile?.totalOutfitsCount)

        viewModel.closeUserProfile()
        assertNull(viewModel.uiState.value.selectedUserProfile)
    }

    @Test
    fun notifications_loadsPendingExpirationNotifs() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadFollowRequests()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.notifications.size)
    }

    @Test
    fun deleteMyPost_removesPostFromFeedConformingToGdpr() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.posts.size)

        viewModel.deleteMyPost("post-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("post-1", fakeSocialRepo.deletedPostId)
        assertEquals(0, viewModel.uiState.value.posts.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("RGPD") == true)
    }

    @Test
    fun reportPost_reportsConformingToDsaAndHidesFromFeed() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.posts.size)

        viewModel.reportPost("post-1", "INAPPROPRIATE", "Contenido no apto")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("post-1", fakeSocialRepo.reportedPostId)
        assertEquals(0, viewModel.uiState.value.posts.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("DSA") == true)
    }

    @Test
    fun blockUser_blocksAndHidesFromFeedConformingToGooglePlayUgcAndDsa() = runTest {
        viewModel.login("joel_m", "secret1234")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.posts.size)

        viewModel.blockUser("user-2", "carlos_fit", block = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("user-2" in fakeSocialRepo.blockedUserIds)
        assertEquals(0, viewModel.uiState.value.posts.size)
        assertTrue(viewModel.uiState.value.userMessage?.contains("bloqueado") == true)
    }
}
