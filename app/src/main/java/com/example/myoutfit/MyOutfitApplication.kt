package com.example.myoutfit

import android.app.Application
import com.example.myoutfit.data.local.db.MyOutfitDatabase
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.local.preferences.UserPreferencesRepositoryImpl
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.ClothingRepositoryImpl
import com.example.myoutfit.data.repository.InspirationRepository
import com.example.myoutfit.data.repository.InspirationRepositoryImpl

import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepositoryImpl
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.data.repository.OutfitRepositoryImpl

class MyOutfitApplication : Application() {
    val database: MyOutfitDatabase by lazy { MyOutfitDatabase.getDatabase(this) }
    val clothingRepository: ClothingRepository by lazy { ClothingRepositoryImpl(database.clothingDao()) }
    val inspirationRepository: InspirationRepository by lazy { InspirationRepositoryImpl(database.inspirationDao()) }
    val outfitRepository: OutfitRepository by lazy { OutfitRepositoryImpl(database.outfitDao()) }
    val loggedOutfitRepository: LoggedOutfitRepository by lazy { LoggedOutfitRepositoryImpl(database.loggedOutfitDao()) }
    val userPreferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepositoryImpl(this) }
}
