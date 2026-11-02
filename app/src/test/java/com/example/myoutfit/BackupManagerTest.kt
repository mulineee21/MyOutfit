package com.example.myoutfit

import com.example.myoutfit.data.backup.ClothingItemBackupDto
import com.example.myoutfit.data.backup.InspirationBackupDto
import com.example.myoutfit.data.backup.LoggedOutfitBackupDto
import com.example.myoutfit.data.backup.MyOutfitBackup
import com.example.myoutfit.data.backup.OutfitBackupDto
import com.example.myoutfit.data.backup.UserPreferencesBackupDto
import com.example.myoutfit.data.backup.WishlistItemBackupDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun backupDataSerialization_serializesAndDeserializesCorrectly() {
        val backup = MyOutfitBackup(
            version = 1,
            exportDate = 1700000000000L,
            appAuthor = "Joel Molina",
            clothingItems = listOf(
                ClothingItemBackupDto(
                    id = 1,
                    imageFileName = "cloth_1.jpg",
                    categoryName = "TOPS",
                    color = "Negro",
                    styleTags = listOf("Gótico", "Casual"),
                    description = "Camiseta negra gráfica",
                    dateAdded = 1700000000000L
                )
            ),
            outfits = listOf(
                OutfitBackupDto(
                    id = 10,
                    title = "Outfit Dark Gym",
                    clothingItemIds = listOf(1),
                    styleTag = "Gótico",
                    isFavorite = true,
                    dateCreated = 1700000000000L
                )
            ),
            inspirations = listOf(
                InspirationBackupDto(
                    id = 100,
                    imageFileName = "insp_1.jpg",
                    title = "Estilo Streetwear",
                    styleTags = listOf("Streetwear"),
                    notes = "Inspiración Pinterest",
                    sourceUrl = "https://pinterest.com"
                )
            ),
            loggedOutfits = listOf(
                LoggedOutfitBackupDto(
                    id = 200,
                    outfitId = 10,
                    clothingItemIds = listOf(1),
                    dateUsed = 1700000000000L,
                    rating = 5.0f,
                    notes = "Gran combinación para concierto"
                )
            ),
            wishlistItems = listOf(
                WishlistItemBackupDto(
                    id = 300,
                    title = "Sudadera Oversize",
                    productUrl = "https://shein.com/item123",
                    imageUrl = "https://img.shein.com/123.jpg",
                    description = "Sudadera con capucha",
                    styleTags = listOf("Gótico", "Streetwear"),
                    category = "Sudaderas",
                    color = "Negro",
                    price = "24.99 €",
                    storeName = "SHEIN",
                    dateAdded = 1700000000000L
                )
            ),
            userPreferences = UserPreferencesBackupDto(
                favoriteStyles = listOf("Gótico", "Casual", "Streetwear"),
                selectedTheme = "SYSTEM",
                geminiApiKey = "AIzaSyTestKey",
                autoCategorizeWithAi = true
            )
        )

        val encoded = json.encodeToString(backup)
        assertTrue(encoded.contains("Sudadera Oversize"))
        assertTrue(encoded.contains("AIzaSyTestKey"))
        assertTrue(encoded.contains("Gótico"))

        val decoded = json.decodeFromString<MyOutfitBackup>(encoded)
        assertEquals(1, decoded.clothingItems.size)
        assertEquals("Negro", decoded.clothingItems[0].color)
        assertEquals(1, decoded.wishlistItems.size)
        assertEquals("SHEIN", decoded.wishlistItems[0].storeName)
        assertEquals(true, decoded.userPreferences?.autoCategorizeWithAi)
        assertEquals("AIzaSyTestKey", decoded.userPreferences?.geminiApiKey)
    }

    @Test
    fun zipArchiveStructure_createsAndReadsExpectedZipEntries() {
        val outputStream = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(outputStream)

        // Add dummy image entry
        zipOut.putNextEntry(ZipEntry("images/cloth_test.jpg"))
        zipOut.write("fake image data".toByteArray())
        zipOut.closeEntry()

        // Add json data entry
        val jsonContent = """{"version": 1, "appAuthor": "Joel Molina"}"""
        zipOut.putNextEntry(ZipEntry("backup_data.json"))
        zipOut.write(jsonContent.toByteArray())
        zipOut.closeEntry()

        zipOut.finish()
        zipOut.flush()

        val zipBytes = outputStream.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Read zip back
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entryNames = mutableListOf<String>()
        var entry = zipIn.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        assertTrue(entryNames.contains("backup_data.json"))
        assertTrue(entryNames.contains("images/cloth_test.jpg"))
    }
}
