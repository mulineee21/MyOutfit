package com.example.myoutfit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myoutfit.ui.main.MainScreen
import com.example.myoutfit.ui.theme.MyOutfitTheme

class MainActivity : ComponentActivity() {

    private var sharedWishlistUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSharedIntent(intent)

        setContent {
            MyOutfitTheme {
                MainScreen(sharedWishlistUrl = sharedWishlistUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val extractedUrl = extractUrl(sharedText)
            if (extractedUrl != null) {
                sharedWishlistUrl = extractedUrl
            }
        }
    }

    private fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val urlRegex = Regex("""https?://[^\s<>"{}|\\^`]+""")
        val match = urlRegex.find(text)
        return match?.value ?: if (text.startsWith("http://") || text.startsWith("https://")) text.trim() else null
    }
}
