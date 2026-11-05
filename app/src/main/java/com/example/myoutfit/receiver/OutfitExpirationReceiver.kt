package com.example.myoutfit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myoutfit.util.OutfitNotificationHelper

class OutfitExpirationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val caption = intent.getStringExtra(EXTRA_CAPTION).orEmpty()
        OutfitNotificationHelper.showExpirationNotification(context, caption)
    }

    companion object {
        const val ACTION_OUTFIT_EXPIRED = "com.example.myoutfit.action.OUTFIT_EXPIRED"
        const val EXTRA_CAPTION = "extra_outfit_caption"
    }
}
