package com.example.myoutfit.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object UriUtils {

    fun createTempImageUri(context: Context): Uri {
        val tempDir = File(context.cacheDir, "camera_temp").apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File.createTempFile(
            "temp_camera_${System.currentTimeMillis()}",
            ".jpg",
            tempDir
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
}
