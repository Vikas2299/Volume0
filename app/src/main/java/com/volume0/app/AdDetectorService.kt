package com.volume0.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class AdDetectorService : NotificationListenerService() {

    private var originalVolume: Int? = null
    private lateinit var audioManager: AudioManager
    private lateinit var enabledApps: Map<String, Int>

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
        val filter = IntentFilter("com.volume0.app.TOGGLE_UPDATE")
        ContextCompat.registerReceiver(this, toggleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "volume0_channel",
                    "Volume0 Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
                val notification = Notification.Builder(this, "volume0_channel")
                    .setContentTitle("Volume0 Running")
                    .setContentText("Detecting songs and ads")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build()
                startForeground(1, notification)
            }
            Log.d(TAG, "Service created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        Log.d(TAG, "Notification posted for package: $packageName")

        if (!enabledApps.containsKey(packageName)) {
            Log.d(TAG, "Ignoring $packageName - Not enabled: $enabledApps")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras
        // Log all extras to debug
        for (key in extras.keySet()) {
            Log.d(TAG, "Extra key: $key, value: ${extras.get(key)}")
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subtitle = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        Log.d(TAG, "[$packageName] Title: $title, Text: $text, Subtitle: $subtitle, BigText: $bigText")

        val status = classifyNotification(packageName, title, text, subtitle)
        val isAd = status.contains("Ad")
        adjustVolume(isAd, packageName)

        NotificationUpdateManager.postNotificationUpdate(status, packageName)
        Log.d(TAG, "Posted status to LiveData: $status")
    }

    private fun classifyNotification(packageName: String, title: String, text: String, subtitle: String): String {
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
                    subtitle.contains("Advertisement", ignoreCase = true) ||
                    subtitle.contains("Sponsored", ignoreCase = true) ||
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

        val currentVolumePercent = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / maxVolume
        Log.d(TAG, "Posting current volume to LiveData: $currentVolumePercent%")
        NotificationUpdateManager.postVolumeUpdate(currentVolumePercent)
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
        try {
            unregisterReceiver(toggleReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Toggle receiver not registered", e)
        }
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