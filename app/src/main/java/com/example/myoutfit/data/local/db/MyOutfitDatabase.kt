package com.example.myoutfit.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myoutfit.data.local.converters.Converters
import com.example.myoutfit.data.local.dao.ClothingDao
import com.example.myoutfit.data.local.dao.InspirationDao
import com.example.myoutfit.data.local.dao.LoggedOutfitDao
import com.example.myoutfit.data.local.dao.OutfitDao
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit

@Database(
    entities = [
        ClothingItem::class,
        Outfit::class,
        InspirationItem::class,
        LoggedOutfit::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MyOutfitDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao
    abstract fun outfitDao(): OutfitDao
    abstract fun inspirationDao(): InspirationDao
    abstract fun loggedOutfitDao(): LoggedOutfitDao

    companion object {
        @Volatile
        private var INSTANCE: MyOutfitDatabase? = null

        fun getDatabase(context: Context): MyOutfitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MyOutfitDatabase::class.java,
                    "my_outfit_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
