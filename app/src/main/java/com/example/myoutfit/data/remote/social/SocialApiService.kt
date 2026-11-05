package com.example.myoutfit.data.remote.social

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialApiService {

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): AuthResponseDto

    @POST("api/auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleAuthRequestDto): AuthResponseDto

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): GenericActionResponseDto

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto): GenericActionResponseDto

    @POST("api/auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Unit>

    @Multipart
    @POST("api/posts")
    suspend fun createPost(
        @Part image: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("wornDate") wornDate: RequestBody,
        @Part("sourceType") sourceType: RequestBody,
        @Part("visibility") visibility: RequestBody,
        @Part("styleTags") styleTagsJson: RequestBody,
        @Part("dominantColors") dominantColorsJson: RequestBody
    ): CreatedPostDto

    @GET("api/feed")
    suspend fun getFeed(
        @Query("limit") limit: Int = 25,
        @Query("before") before: String? = null,
        @Query("discover") discover: Boolean = false
    ): FeedResponseDto

    @POST("api/posts/{postId}/reactions")
    suspend fun reactToPost(
        @Path("postId") postId: String,
        @Body body: ReactionRequestDto
    ): ReactionResponseDto

    @DELETE("api/posts/{postId}/reactions")
    suspend fun removeReaction(
        @Path("postId") postId: String
    ): Response<Unit>

    @GET("api/posts/{postId}/comments")
    suspend fun getComments(
        @Path("postId") postId: String
    ): CommentsResponseDto

    @POST("api/posts/{postId}/comments")
    suspend fun addComment(
        @Path("postId") postId: String,
        @Body body: CreateCommentRequestDto
    ): CreatedCommentDto

    @GET("api/calendar/{userId}")
    suspend fun getMonthlyCalendar(
        @Path("userId") userId: String,
        @Query("year") year: Int,
        @Query("month") month: Int
    ): CalendarMonthResponseDto

    @GET("api/calendar/{userId}/recap")
    suspend fun getYearlyRecap(
        @Path("userId") userId: String,
        @Query("year") year: Int
    ): YearlyRecapDto

    @POST("api/users/follow")
    suspend fun followOrUnfollow(
        @Body body: FollowActionRequestDto
    ): FollowActionResponseDto

    @GET("api/users/{userId}/profile")
    suspend fun getUserProfile(
        @Path("userId") userId: String
    ): UserProfileDto

    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): SearchUsersResponseDto

    @GET("api/users/follow-requests")
    suspend fun getPendingFollowRequests(): FollowRequestsResponseDto

    @GET("api/users/notifications")
    suspend fun getNotifications(): UserNotificationsResponseDto

    @POST("api/users/follow-requests/{followerId}")
    suspend fun respondToFollowRequest(
        @Path("followerId") followerId: String,
        @Body body: FollowDecisionRequestDto
    ): Response<Unit>

    @GET("api/users/me")
    suspend fun getMyProfile(): MeProfileDto

    @PATCH("api/users/me")
    suspend fun updateMyProfile(@Body body: UpdateProfileRequestDto): UpdatedProfileDto

    @PATCH("api/users/me/username")
    suspend fun updateMyUsername(@Body body: UpdateUsernameRequestDto): UpdateUsernameResponseDto

    @Multipart
    @POST("api/users/me/avatar")
    suspend fun uploadMyAvatar(@Part avatar: MultipartBody.Part): AvatarUploadResponseDto

    @GET("api/users/me/privacy-export")
    suspend fun exportMyGdprData(): okhttp3.ResponseBody

    @DELETE("api/users/me")
    suspend fun deleteMyAccount(): GenericActionResponseDto

    @DELETE("api/posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String,
        @Query("purgeArchive") purgeArchive: Boolean = true
    ): GenericActionResponseDto

    @POST("api/posts/{postId}/report")
    suspend fun reportPost(
        @Path("postId") postId: String,
        @Body body: ReportPostRequestDto
    ): GenericActionResponseDto

    @POST("api/users/block")
    suspend fun blockUser(
        @Body body: BlockUserRequestDto
    ): GenericActionResponseDto

    @GET("api/users/blocked")
    suspend fun getBlockedUsers(): BlockedUsersResponseDto
}
