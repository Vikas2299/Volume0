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
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class AdDetectorService : NotificationListenerService() {

//    private var originalVolume: Int? = null // Legacy variable, not used
    private lateinit var audioManager: AudioManager
    private lateinit var enabledApps: Map<String, Int>
    private var lastVolumeChangeTime = 0L
    private var lastPackageName: String? = null
    private var lastIsAd: Boolean? = null
    private val lastAdState = mutableMapOf<String, Boolean>() // Track ad state per package
    private val VOLUME_CHANGE_DEBOUNCE_MS = 500L
    private val VOLUME_CHANGE_RETRIES = 3
    private val VOLUME_CHANGE_RETRY_DELAY_MS = 100L
    private val DEFAULT_RESTORE_VOLUME_PERCENT = 50
    private val originalVolumes = mutableMapOf<String, Int?>()

    private val handler = Handler(Looper.getMainLooper())

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

    private fun adjustVolume(isAd: Boolean, packageName: String) {
        val currentTime = System.currentTimeMillis()

        if (lastPackageName == packageName && lastIsAd == isAd) {
            Log.d(TAG, "Skipping volume adjustment: No change in ad state for $packageName (isAd=$isAd, lastIsAd=$lastIsAd)")
            return
        }

        if (currentTime - lastVolumeChangeTime < VOLUME_CHANGE_DEBOUNCE_MS) {
            Log.d(TAG, "Debouncing volume change: Too soon since last change")
            return
        }

        lastVolumeChangeTime = currentTime
        val previousPackageName = lastPackageName
        val previousIsAd = lastIsAd
        lastPackageName = packageName
        lastIsAd = isAd
        Log.d(TAG, "Updated state: previousPackageName=$previousPackageName, previousIsAd=$previousIsAd, lastPackageName=$lastPackageName, lastIsAd=$lastIsAd")

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val adVolumePercent = enabledApps[packageName] ?: 0
        val adVolume = (maxVolume * adVolumePercent) / 100

        Log.d(TAG, "Adjusting volume: isAd=$isAd, packageName=$packageName, adVolumePercent=$adVolumePercent, currentVolume=$currentVolume, maxVolume=$maxVolume, originalVolume=${originalVolumes[packageName]}, lastAdState=${lastAdState[packageName]}")

        if (!isAd && originalVolumes[packageName] == null) {
            originalVolumes[packageName] = currentVolume
            Log.d(TAG, "Non-ad detected (no prior volume stored): Stored volume for $packageName: ${originalVolumes[packageName]}")

            val currentVolumePercent = (currentVolume * 100) / maxVolume
            Log.d(TAG, "Posting current volume to LiveData: $currentVolumePercent%")
            NotificationUpdateManager.postVolumeUpdate(currentVolumePercent)
        }

        val wasAd = lastAdState[packageName] ?: false
        lastAdState[packageName] = isAd
        Log.d(TAG, "Updated ad state for $packageName: wasAd=$wasAd, isAd=$isAd")

        if (isAd) {
            if (originalVolumes[packageName] == null) {
                originalVolumes[packageName] = currentVolume
                Log.d(TAG, "Ad detected (no prior volume stored): Stored volume for $packageName: ${originalVolumes[packageName]}")
            }
            setVolumeWithRetry(
                AudioManager.STREAM_MUSIC,
                adVolume,
                "Setting ad volume to $adVolume ($adVolumePercent% of max $maxVolume)"
            ) { finalVolume ->
                val finalVolumePercent = (finalVolume * 100) / maxVolume
                Log.d(TAG, "Posting current volume to LiveData: $finalVolumePercent%")
                NotificationUpdateManager.postVolumeUpdate(finalVolumePercent)
            }
        } else if (wasAd) { // Check per-package ad state instead of lastIsAd
            val originalVolume = originalVolumes[packageName]
            Log.d(TAG, "Transition from ad to non-ad detected for $packageName: wasAd=$wasAd, attempting to restore original volume to $originalVolume")
            if (originalVolume != null && currentVolume == adVolume) { // Additional check to ensure we're in ad volume state
                setVolumeWithRetry(
                    AudioManager.STREAM_MUSIC,
                    originalVolume,
                    "Restoring volume to $originalVolume"
                ) { finalVolume ->
                    originalVolumes.remove(packageName)
                    lastAdState[packageName] = false // Reset ad state after restoration
                    Log.d(TAG, "Cleared stored volume for $packageName")
                    val finalVolumePercent = (finalVolume * 100) / maxVolume
                    Log.d(TAG, "Posting current volume to LiveData: $finalVolumePercent%")
                    NotificationUpdateManager.postVolumeUpdate(finalVolumePercent)
                }
            } else if (originalVolume == null) {
                val defaultVolume = (maxVolume * DEFAULT_RESTORE_VOLUME_PERCENT) / 100
                setVolumeWithRetry(
                    AudioManager.STREAM_MUSIC,
                    defaultVolume,
                    "No stored volume for $packageName, using default volume $defaultVolume"
                ) { finalVolume ->
                    lastAdState[packageName] = false // Reset ad state after restoration
                    Log.w(TAG, "No stored volume for $packageName, restored to default $defaultVolume")
                    val finalVolumePercent = (finalVolume * 100) / maxVolume
                    Log.d(TAG, "Posting current volume to LiveData: $finalVolumePercent%")
                    NotificationUpdateManager.postVolumeUpdate(finalVolumePercent)
                }
            } else {
                Log.d(TAG, "Volume restoration skipped: currentVolume=$currentVolume, adVolume=$adVolume, originalVolume=$originalVolume")
            }
        } else {
            Log.d(TAG, "No volume restoration needed for $packageName: wasAd=$wasAd, isAd=$isAd")
        }
    }

    private fun setVolumeWithRetry(
        streamType: Int,
        volume: Int,
        logMessage: String,
        retryCount: Int = 0,
        onComplete: ((Int) -> Unit)? = null
    ) {
        try {
            Log.d(TAG, "$logMessage (attempt ${retryCount + 1})")
            val initialVolume = audioManager.getStreamVolume(streamType)
            Log.d(TAG, "Initial volume before change: $initialVolume")
            audioManager.setStreamVolume(streamType, volume, 0)
            val immediateVolume = audioManager.getStreamVolume(streamType)
            Log.d(TAG, "Volume immediately after set: $immediateVolume (target: $volume)")

            handler.postDelayed({
                val actualVolume = audioManager.getStreamVolume(streamType)
                Log.d(TAG, "Volume after delay: $actualVolume (target: $volume)")
                if (actualVolume != volume) {
                    if (retryCount < VOLUME_CHANGE_RETRIES) {
                        Log.w(TAG, "Volume set to $actualVolume, expected $volume. Retrying (${retryCount + 1}/$VOLUME_CHANGE_RETRIES)")
                        setVolumeWithRetry(streamType, volume, logMessage, retryCount + 1, onComplete)
                    } else {
                        Log.e(TAG, "Failed to set volume to $volume after $VOLUME_CHANGE_RETRIES retries. Actual volume: $actualVolume")
                        onComplete?.invoke(actualVolume)
                    }
                } else {
                    Log.d(TAG, "Volume successfully set to $volume")
                    onComplete?.invoke(actualVolume)
                }
            }, VOLUME_CHANGE_RETRY_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}", e)
            onComplete?.invoke(audioManager.getStreamVolume(streamType)) // Fallback to current volume
        }
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
        handler.removeCallbacksAndMessages(null)
        originalVolumes.clear()
        lastAdState.clear()
        lastPackageName = null
        lastIsAd = null
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