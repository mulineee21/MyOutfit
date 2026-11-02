package com.example.myoutfit.data.backup

import android.content.Context
import android.util.Log
import com.example.myoutfit.data.local.db.MyOutfitDatabase
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.data.local.entity.WishlistItem
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: MyOutfitDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getStatsFlow(): Flow<BackupStats> {
        return combine(
            database.clothingDao().getAllClothingItems(),
            database.outfitDao().getAllOutfits(),
            database.inspirationDao().getAllInspirationItems(),
            database.loggedOutfitDao().getAllLoggedOutfits(),
            database.wishlistDao().getAllWishlistItems()
        ) { clothing, outfits, inspirations, logged, wishlist ->
            val imageDir = ImageStorageManager.getImagesDirectory(context)
            val imageFiles = imageDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()

            BackupStats(
                clothingCount = clothing.size,
                outfitCount = outfits.size,
                inspirationCount = inspirations.size,
                loggedCount = logged.size,
                wishlistCount = wishlist.size,
                imageCount = imageFiles.size,
                fileSizeBytes = imageFiles.sumOf { it.length() }
            )
        }
    }

    suspend fun createShareableBackupZip(): Pair<File, BackupStats> = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "myoutfit_backup_$timeStamp.zip"
        val zipFile = File(context.cacheDir, fileName)

        val stats = FileOutputStream(zipFile).use { outputStream ->
            exportBackup(outputStream)
        }

        Pair(zipFile, stats.copy(fileSizeBytes = zipFile.length()))
    }

    suspend fun exportBackup(outputStream: OutputStream): BackupStats = withContext(Dispatchers.IO) {
        val clothingList = database.clothingDao().getAllClothingItems().first()
        val outfitList = database.outfitDao().getAllOutfits().first()
        val inspirationList = database.inspirationDao().getAllInspirationItems().first()
        val loggedList = database.loggedOutfitDao().getAllLoggedOutfits().first()
        val wishlistList = database.wishlistDao().getAllWishlistItems().first()
        val userPrefs = userPreferencesRepository.userPreferencesFlow.first()

        val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
        val writtenImageNames = mutableSetOf<String>()
        var exportedImageCount = 0

        fun archiveImage(originalPath: String, prefix: String): String {
            if (originalPath.isBlank()) return ""
            val file = File(originalPath)
            if (!file.exists() || !file.isFile) return ""

            val entryName = "${prefix}_${file.name}"
            if (!writtenImageNames.contains(entryName)) {
                try {
                    zipOut.putNextEntry(ZipEntry("images/$entryName"))
                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    writtenImageNames.add(entryName)
                    exportedImageCount++
                } catch (e: Exception) {
                    Log.w("BackupManager", "Error al archivar imagen $originalPath", e)
                }
            }
            return entryName
        }

        // 1. Process clothing items
        val clothingDtos = clothingList.map { item ->
            val imageName = archiveImage(item.imagePath, "cloth")
            ClothingItemBackupDto(
                id = item.id,
                imageFileName = imageName,
                categoryName = item.category.name,
                color = item.color,
                styleTags = item.styleTags,
                description = item.description,
                dateAdded = item.dateAdded
            )
        }

        // 2. Process outfits
        val outfitDtos = outfitList.map { outfit ->
            OutfitBackupDto(
                id = outfit.id,
                title = outfit.title,
                clothingItemIds = outfit.clothingItemIds,
                styleTag = outfit.styleTag,
                isFavorite = outfit.isFavorite,
                dateCreated = outfit.dateCreated
            )
        }

        // 3. Process inspirations
        val inspirationDtos = inspirationList.map { insp ->
            val imageName = archiveImage(insp.imagePath, "insp")
            InspirationBackupDto(
                id = insp.id,
                imageFileName = imageName,
                title = insp.title,
                styleTags = insp.styleTags,
                notes = insp.notes,
                sourceUrl = insp.sourceUrl
            )
        }

        // 4. Process logged outfits
        val loggedDtos = loggedList.map { log ->
            LoggedOutfitBackupDto(
                id = log.id,
                outfitId = log.outfitId,
                clothingItemIds = log.clothingItemIds,
                dateUsed = log.dateUsed,
                rating = log.rating,
                notes = log.notes
            )
        }

        // 5. Process wishlist items
        val wishlistDtos = wishlistList.map { wish ->
            val imageName = if (wish.imageUrl.isNotBlank() && !wish.imageUrl.startsWith("http")) {
                archiveImage(wish.imageUrl, "wish")
            } else ""
            WishlistItemBackupDto(
                id = wish.id,
                title = wish.title,
                productUrl = wish.productUrl,
                imageUrl = if (imageName.isNotBlank()) imageName else wish.imageUrl,
                description = wish.description,
                styleTags = wish.styleTags,
                category = wish.category,
                color = wish.color,
                price = wish.price,
                storeName = wish.storeName,
                dateAdded = wish.dateAdded
            )
        }

        // 6. User preferences DTO
        val prefsDto = UserPreferencesBackupDto(
            favoriteStyles = userPrefs.favoriteStyles.toList(),
            selectedTheme = userPrefs.selectedTheme,
            geminiApiKey = userPrefs.geminiApiKey,
            autoCategorizeWithAi = userPrefs.autoCategorizeWithAi
        )

        val backupData = MyOutfitBackup(
            version = 1,
            exportDate = System.currentTimeMillis(),
            clothingItems = clothingDtos,
            outfits = outfitDtos,
            inspirations = inspirationDtos,
            loggedOutfits = loggedDtos,
            wishlistItems = wishlistDtos,
            userPreferences = prefsDto
        )

        // Write JSON data entry
        val jsonBytes = json.encodeToString(backupData).toByteArray(Charsets.UTF_8)
        zipOut.putNextEntry(ZipEntry("backup_data.json"))
        zipOut.write(jsonBytes)
        zipOut.closeEntry()

        zipOut.finish()
        zipOut.flush()

        BackupStats(
            clothingCount = clothingDtos.size,
            outfitCount = outfitDtos.size,
            inspirationCount = inspirationDtos.size,
            loggedCount = loggedDtos.size,
            wishlistCount = wishlistDtos.size,
            imageCount = exportedImageCount
        )
    }

    suspend fun importBackup(inputStream: InputStream, replaceAll: Boolean): BackupStats = withContext(Dispatchers.IO) {
        val zipIn = ZipInputStream(BufferedInputStream(inputStream))
        val targetImagesDir = ImageStorageManager.getImagesDirectory(context)
        if (!targetImagesDir.exists()) targetImagesDir.mkdirs()

        var jsonContent: String? = null
        val restoredImageMap = mutableMapOf<String, String>() // zipImageName -> newAbsolutePath
        var importedImageCount = 0

        var entry = zipIn.nextEntry
        while (entry != null) {
            val entryName = entry.name
            if (entryName == "backup_data.json") {
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                while (zipIn.read(buffer).also { len = it } > 0) {
                    byteBuffer.write(buffer, 0, len)
                }
                jsonContent = byteBuffer.toString(Charsets.UTF_8.name())
            } else if (entryName.startsWith("images/") && !entry.isDirectory) {
                val fileName = entryName.removePrefix("images/")
                val destFile = File(targetImagesDir, fileName)
                FileOutputStream(destFile).use { out ->
                    zipIn.copyTo(out)
                }
                restoredImageMap[fileName] = destFile.absolutePath
                importedImageCount++
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        if (jsonContent.isNullOrBlank()) {
            throw IllegalArgumentException("El archivo seleccionado no es una copia de seguridad válida de MyOutfit (falta backup_data.json).")
        }

        val backup = json.decodeFromString<MyOutfitBackup>(jsonContent)

        if (replaceAll) {
            database.clearAllTables()
        }

        val oldToNewClothingIdMap = mutableMapOf<Long, Long>()
        val oldToNewOutfitIdMap = mutableMapOf<Long, Long>()

        // 1. Restore Clothing Items
        for (dto in backup.clothingItems) {
            val restoredPath = restoredImageMap[dto.imageFileName] ?: ""
            val category = try {
                ClothingCategory.valueOf(dto.categoryName)
            } catch (_: Exception) {
                ClothingCategory.TOPS
            }
            val item = ClothingItem(
                id = if (replaceAll) dto.id else 0,
                imagePath = restoredPath,
                category = category,
                color = dto.color,
                styleTags = dto.styleTags,
                description = dto.description,
                dateAdded = dto.dateAdded
            )
            val newId = database.clothingDao().insertClothingItem(item)
            oldToNewClothingIdMap[dto.id] = newId
        }

        // 2. Restore Outfits
        for (dto in backup.outfits) {
            val mappedIds = dto.clothingItemIds.mapNotNull { oldId ->
                oldToNewClothingIdMap[oldId] ?: if (!replaceAll) oldId else null
            }
            val outfit = Outfit(
                id = if (replaceAll) dto.id else 0,
                title = dto.title,
                clothingItemIds = mappedIds,
                styleTag = dto.styleTag,
                isFavorite = dto.isFavorite,
                dateCreated = dto.dateCreated
            )
            val newOutfitId = database.outfitDao().insertOutfit(outfit)
            oldToNewOutfitIdMap[dto.id] = newOutfitId
        }

        // 3. Restore Inspirations
        for (dto in backup.inspirations) {
            val restoredPath = restoredImageMap[dto.imageFileName] ?: ""
            val item = InspirationItem(
                id = if (replaceAll) dto.id else 0,
                imagePath = restoredPath,
                title = dto.title,
                styleTags = dto.styleTags,
                notes = dto.notes,
                sourceUrl = dto.sourceUrl
            )
            database.inspirationDao().insertInspirationItem(item)
        }

        // 4. Restore Logged Outfits
        for (dto in backup.loggedOutfits) {
            val mappedOutfitId = dto.outfitId?.let { oldToNewOutfitIdMap[it] ?: it }
            val mappedClothingIds = dto.clothingItemIds.mapNotNull { oldId ->
                oldToNewClothingIdMap[oldId] ?: if (!replaceAll) oldId else null
            }
            val logged = LoggedOutfit(
                id = if (replaceAll) dto.id else 0,
                outfitId = mappedOutfitId,
                clothingItemIds = mappedClothingIds,
                dateUsed = dto.dateUsed,
                rating = dto.rating,
                notes = dto.notes
            )
            database.loggedOutfitDao().insertLoggedOutfit(logged)
        }

        // 5. Restore Wishlist Items
        for (dto in backup.wishlistItems) {
            val finalImageUrl = restoredImageMap[dto.imageUrl] ?: dto.imageUrl
            val item = WishlistItem(
                id = if (replaceAll) dto.id else 0,
                title = dto.title,
                productUrl = dto.productUrl,
                imageUrl = finalImageUrl,
                description = dto.description,
                styleTags = dto.styleTags,
                category = dto.category,
                color = dto.color,
                price = dto.price,
                storeName = dto.storeName,
                dateAdded = dto.dateAdded
            )
            database.wishlistDao().insertWishlistItem(item)
        }

        // 6. Restore User Preferences
        backup.userPreferences?.let { p ->
            if (p.favoriteStyles.isNotEmpty()) {
                userPreferencesRepository.updateFavoriteStyles(p.favoriteStyles.toSet())
            }
            if (p.selectedTheme.isNotBlank()) {
                userPreferencesRepository.updateSelectedTheme(p.selectedTheme)
            }
            if (p.geminiApiKey.isNotBlank()) {
                userPreferencesRepository.updateGeminiApiKey(p.geminiApiKey)
            }
            userPreferencesRepository.updateAutoCategorizeWithAi(p.autoCategorizeWithAi)
        }

        BackupStats(
            clothingCount = backup.clothingItems.size,
            outfitCount = backup.outfits.size,
            inspirationCount = backup.inspirations.size,
            loggedCount = backup.loggedOutfits.size,
            wishlistCount = backup.wishlistItems.size,
            imageCount = importedImageCount
        )
    }
}
