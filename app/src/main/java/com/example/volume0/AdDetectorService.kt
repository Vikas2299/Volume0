package com.example.volume0

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class AdDetectorService : NotificationListenerService() {

    private var originalVolume: Int? = null
    private lateinit var audioManager: AudioManager
    private lateinit var enabledApps: Map<String, Int> // Map of packageName to adVolume

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received toggle update broadcast")
            updateEnabledApps()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        updateEnabledApps()
        val filter = IntentFilter("com.example.volume0.TOGGLE_UPDATE")
        ContextCompat.registerReceiver(this, toggleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        Log.d(TAG, "Service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        Log.d(TAG, "Notification posted for: $packageName")

        if (!enabledApps.containsKey(packageName)) {
            Log.d(TAG, "Ignoring $packageName - Not enabled: $enabledApps")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        Log.d(TAG, "[$packageName] Title: $title, Text: $text")

        val status = classifyNotification(packageName, title, text)
        val isAd = status.contains("Ad")
        adjustVolume(isAd, packageName)

        val intent = Intent("com.example.volume0.NOTIFICATION_UPDATE")
        intent.putExtra("status", status)
        intent.putExtra("packageName", packageName)
        sendBroadcast(intent)
        Log.d(TAG, "Broadcasted status: $status")
    }

    private fun classifyNotification(packageName: String, title: String, text: String): String {
        val appName = AppConfigManager.supportedApps.find { it.packageName == packageName }?.name ?: "Unknown App"
        val contentType = when (packageName) {
            "com.soundcloud.android", "com.spotify.music", "com.pandora.android" -> "Song"
            else -> "Video"
        }

        return when {
            title.contains("Advertisement", ignoreCase = true) ||
                    title.contains("Sponsored", ignoreCase = true) ||
                    text.contains("Advertisement", ignoreCase = true) ||
                    text.contains("Sponsored", ignoreCase = true) ||
                    text.isEmpty() -> "Playing: Ad ($appName)"
            title.isNotEmpty() && text.isNotEmpty() -> "Playing: $contentType ($appName)\n$title\n$text"
            else -> "Playing: Unknown ($appName)"
        }
    }

    private fun adjustVolume(isAd: Boolean, packageName: String) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val adVolumePercent = enabledApps[packageName] ?: 50

        Log.d(TAG, "Adjusting volume: isAd=$isAd, packageName=$packageName, adVolumePercent=$adVolumePercent, currentVolume=$currentVolume, maxVolume=$maxVolume")

        if (isAd) {
            if (originalVolume == null) {
                originalVolume = currentVolume
                Log.d(TAG, "Stored original volume: $originalVolume")
            }
            // Calculate the target volume based on the percentage
            val adVolume = (maxVolume * adVolumePercent) / 100
            Log.d(TAG, "Setting ad volume to $adVolume ($adVolumePercent% of max $maxVolume)")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, adVolume, 0)
        } else {
            originalVolume?.let { volume ->
                Log.d(TAG, "Restoring volume to $volume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                originalVolume = null
            }
        }

        // Broadcast current volume
        val currentVolumePercent = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVolume
        Log.d(TAG, "Broadcasting current volume: $currentVolumePercent%")
        val intent = Intent("com.example.volume0.VOLUME_UPDATE")
        intent.putExtra("volume", currentVolumePercent)
        sendBroadcast(intent)
    }

    private fun updateEnabledApps() {
        enabledApps = AppConfigManager.getAppConfigs(this)
            .filter { it.isEnabled }
            .associate { it.packageName to it.adVolume }
        Log.d(TAG, "Enabled apps updated: $enabledApps")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateEnabledApps()
        Log.d(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(toggleReceiver)
        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "AdDetectorService"

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val enabledListeners = getEnabledNotificationListeners(context)
            return enabledListeners?.contains(pkgName) == true
        }

        private fun getEnabledNotificationListeners(context: Context): Set<String>? {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return enabled?.split(":")?.map { ComponentName.unflattenFromString(it)?.packageName ?: "" }?.toSet()
        }
    }
}