package com.example.myoutfit.data.remote.social

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String,
    val bio: String = "",
    val isPrivate: Boolean = true,
    val acceptedPrivacyPolicy: Boolean = true,
    val confirmedAge14Plus: Boolean = true,
    val deviceInfo: String = "Android"
)

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    val identifier: String,
    val password: String,
    val deviceInfo: String = "Android"
)

@JsonClass(generateAdapter = true)
data class RefreshRequestDto(
    val refreshToken: String,
    val deviceInfo: String = "Android"
)

@JsonClass(generateAdapter = true)
data class GoogleAuthRequestDto(
    val idToken: String,
    val deviceInfo: String = "Android"
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequestDto(
    val email: String
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequestDto(
    val email: String,
    val code: String,
    val newPassword: String
)

@JsonClass(generateAdapter = true)
data class GenericActionResponseDto(
    val ok: Boolean = true,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class LogoutRequestDto(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SocialUserDto(
    val id: String,
    val username: String,
    val email: String = "",
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val totalOutfitsCount: Int = 0,
    val currentStreak: Int = 0,
    val canChangeUsername: Boolean = true,
    val nextUsernameChangeAt: String? = null,
    val usernameCooldownDaysRemaining: Int = 0,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: SocialUserDto
)

@JsonClass(generateAdapter = true)
data class RefreshResponseDto(
    val accessToken: String,
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class PostAuthorDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val followStatus: String = "NONE"
)

@JsonClass(generateAdapter = true)
data class FeedPostDto(
    val postId: String,
    val archiveId: String,
    val author: PostAuthorDto,
    val imageUrl: String,
    val thumbnailUrl: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val caption: String = "",
    val visibility: String = "FOLLOWERS",
    val sourceType: String = "REAL_CAMERA",
    val styleTags: List<String> = emptyList(),
    val dominantColors: List<String> = emptyList(),
    val isLate: Boolean = false,
    val reactionsCount: Int = 0,
    val commentsCount: Int = 0,
    val myReaction: String? = null,
    val publishedAt: String,
    val expiresAt: String,
    val remainingSeconds: Int
)

@JsonClass(generateAdapter = true)
data class FeedResponseDto(
    val hasPostedToday: Boolean = false,
    val nextCursor: String? = null,
    val items: List<FeedPostDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreatedPostDto(
    val postId: String,
    val archiveId: String,
    val wornDate: String,
    val visibility: String,
    val caption: String,
    val sourceType: String,
    val styleTags: List<String> = emptyList(),
    val dominantColors: List<String> = emptyList(),
    val imageUrl: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val expiresAt: String,
    val remainingSeconds: Int
)

@JsonClass(generateAdapter = true)
data class ReactionRequestDto(
    val emoji: String
)

@JsonClass(generateAdapter = true)
data class ReactionResponseDto(
    val ok: Boolean,
    val emoji: String
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    val id: String,
    val author: PostAuthorDto,
    val content: String,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class CommentsResponseDto(
    val comments: List<CommentDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequestDto(
    val content: String
)

@JsonClass(generateAdapter = true)
data class CreatedCommentDto(
    val id: String,
    val content: String,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class CalendarEntryDto(
    val archiveId: String,
    val wornDate: String,
    val isPrimaryOfDay: Boolean = true,
    val thumbnailUrl: String,
    val fullImageUrl: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val sourceType: String = "REAL_CAMERA",
    val caption: String = "",
    val styleTags: List<String> = emptyList(),
    val dominantColors: List<String> = emptyList(),
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class CalendarMonthResponseDto(
    val userId: String,
    val year: Int,
    val month: Int,
    val totalLoggedDaysInMonth: Int = 0,
    val entries: List<CalendarEntryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StyleUsageDto(
    val style: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class ColorUsageDto(
    val color: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class MonthDaysDto(
    val month: Int,
    val days: Int
)

@JsonClass(generateAdapter = true)
data class YearlyRecapDto(
    val year: Int,
    val totalOutfits: Int = 0,
    val uniqueDaysLogged: Int = 0,
    val topStyles: List<StyleUsageDto> = emptyList(),
    val topColors: List<ColorUsageDto> = emptyList(),
    val daysByMonth: List<MonthDaysDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FollowActionRequestDto(
    val targetUserId: String,
    val action: String = "FOLLOW"
)

@JsonClass(generateAdapter = true)
data class FollowActionResponseDto(
    val targetUserId: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class SearchedUserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val totalOutfitsCount: Int = 0,
    val followStatus: String = "NONE"
)

@JsonClass(generateAdapter = true)
data class SearchUsersResponseDto(
    val users: List<SearchedUserDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FollowRequestItemDto(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val requestedAt: String
)

@JsonClass(generateAdapter = true)
data class FollowRequestsResponseDto(
    val requests: List<FollowRequestItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FollowDecisionRequestDto(
    val decision: String
)

@JsonClass(generateAdapter = true)
data class UserNotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val createdAt: String,
    val readAt: String? = null
)

@JsonClass(generateAdapter = true)
data class UserNotificationsResponseDto(
    val notifications: List<UserNotificationDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MeProfileDto(
    val id: String,
    val username: String,
    val email: String = "",
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val totalOutfitsCount: Int = 0,
    val currentStreak: Int = 0,
    val canChangeUsername: Boolean = true,
    val nextUsernameChangeAt: String? = null,
    val usernameCooldownDaysRemaining: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val pendingRequestsCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequestDto(
    val displayName: String? = null,
    val bio: String? = null,
    val isPrivate: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class UpdatedProfileDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val isPrivate: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UpdateUsernameRequestDto(
    val username: String
)

@JsonClass(generateAdapter = true)
data class UpdateUsernameResponseDto(
    val username: String,
    val displayName: String,
    val canChangeUsername: Boolean = false,
    val nextUsernameChangeAt: String? = null,
    val usernameCooldownDaysRemaining: Int = 30,
    val accessToken: String? = null
)

@JsonClass(generateAdapter = true)
data class AvatarUploadResponseDto(
    val avatarUrl: String
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val totalOutfitsCount: Int = 0,
    val currentStreak: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val followStatus: String = "NONE",
    val isBlockedByMe: Boolean = false,
    val canViewPosts: Boolean = true,
    val activePost: FeedPostDto? = null
)

@JsonClass(generateAdapter = true)
data class ReportPostRequestDto(
    val reason: String,
    val details: String = ""
)

@JsonClass(generateAdapter = true)
data class BlockUserRequestDto(
    val targetUserId: String,
    val action: String = "BLOCK"
)

@JsonClass(generateAdapter = true)
data class BlockedUserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val blockedAt: String
)

@JsonClass(generateAdapter = true)
data class BlockedUsersResponseDto(
    val blockedUsers: List<BlockedUserDto> = emptyList()
)
