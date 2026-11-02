package com.example.myoutfit.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageManager {

    private const val DIRECTORY_NAME = "my_outfit_images"

    fun getImagesDirectory(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, fileNamePrefix: String = "img"): String {
        val fileName = "${fileNamePrefix}_${UUID.randomUUID()}.jpg"
        val file = File(getImagesDirectory(context), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    fun saveUri(context: Context, uri: Uri, fileNamePrefix: String = "img"): String? {
        return try {
            val fileName = "${fileNamePrefix}_${UUID.randomUUID()}.jpg"
            val file = File(getImagesDirectory(context), fileName)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveRemoteUrl(context: Context, imageUrl: String, fileNamePrefix: String = "img"): String? {
        return try {
            if (imageUrl.isBlank()) return null
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                return imageUrl
            }
            val fileName = "${fileNamePrefix}_${UUID.randomUUID()}.jpg"
            val file = File(getImagesDirectory(context), fileName)
            val url = java.net.URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getImageFile(imagePath: String): File? {
        val file = File(imagePath)
        return if (file.exists()) file else null
    }

    fun deleteImage(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            file.exists() && file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
