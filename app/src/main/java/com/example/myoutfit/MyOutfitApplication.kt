package com.example.myoutfit

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.myoutfit.data.backup.BackupManager
import com.example.myoutfit.data.local.db.MyOutfitDatabase
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.local.preferences.UserPreferencesRepositoryImpl
import com.example.myoutfit.data.remote.social.SocialNetworkClient
import com.example.myoutfit.data.remote.social.SocialSessionManager
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.ClothingRepositoryImpl
import com.example.myoutfit.data.repository.InspirationRepository
import com.example.myoutfit.data.repository.InspirationRepositoryImpl
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepositoryImpl
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.data.repository.OutfitRepositoryImpl
import com.example.myoutfit.data.repository.SocialRepository
import com.example.myoutfit.data.repository.SocialRepositoryImpl
import com.example.myoutfit.data.repository.WishlistRepository
import com.example.myoutfit.data.repository.WishlistRepositoryImpl

class MyOutfitApplication : Application(), ImageLoaderFactory {
    val database: MyOutfitDatabase by lazy { MyOutfitDatabase.getDatabase(this) }
    val clothingRepository: ClothingRepository by lazy { ClothingRepositoryImpl(database.clothingDao()) }
    val inspirationRepository: InspirationRepository by lazy { InspirationRepositoryImpl(database.inspirationDao()) }
    val outfitRepository: OutfitRepository by lazy { OutfitRepositoryImpl(database.outfitDao()) }
    val loggedOutfitRepository: LoggedOutfitRepository by lazy { LoggedOutfitRepositoryImpl(database.loggedOutfitDao()) }
    val wishlistRepository: WishlistRepository by lazy { WishlistRepositoryImpl(database.wishlistDao()) }
    val userPreferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepositoryImpl(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, database, userPreferencesRepository) }

    // Capa de Red Social Descentralizada / Autohospedada ("BeReal de Outfits" en Debian)
    val socialSessionManager: SocialSessionManager by lazy { SocialSessionManager(this) }
    val socialNetworkClient: SocialNetworkClient by lazy { SocialNetworkClient(this, socialSessionManager) }
    val socialRepository: SocialRepository by lazy {
        SocialRepositoryImpl(
            apiService = socialNetworkClient.apiService,
            sessionManager = socialSessionManager
        )
    }

    override fun newImageLoader(): ImageLoader {
        return socialNetworkClient.authenticatedImageLoader
    }
}
